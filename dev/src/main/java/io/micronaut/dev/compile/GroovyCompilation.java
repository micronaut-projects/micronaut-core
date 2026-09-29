/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.compile;

import groovy.lang.GroovyClassLoader;
import io.micronaut.core.annotation.Internal;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One run of groovyc. Everything that touches a Groovy type is here, loaded only when Groovy is
 * present.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class GroovyCompilation {

    private GroovyCompilation() {
    }

    /**
     * Compiles the sources into the staging directory.
     *
     * @return What was produced, or null when the compilation failed
     */
    static StagedSourceCompiler.@Nullable Produced run(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, List<CompileDiagnostic> diagnostics) throws IOException {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setTargetDirectory(staging.toFile());
        configuration.setSourceEncoding("UTF-8");
        configuration.setParameters(true);
        List<String> classpath = new ArrayList<>();
        for (Path entry : request.compileClasspath()) {
            classpath.add(entry.toString());
        }
        if (Files.isDirectory(request.classOutput())) {
            // the unchanged classes, and what another language wrote into a shared output, resolve from the
            // output; the hiding loader below keeps this compilation's own previous outputs out of sight
            classpath.add(request.classOutput().toString());
        }
        for (Path entry : request.processorPath()) {
            // the AST transformations are discovered from the loader: the processor path holds them
            classpath.add(entry.toString());
        }
        // the classpath belongs to the hiding loader below, not to the configuration: what the configuration
        // lists is added to the Groovy loader itself, past anything a parent hides
        for (String option : request.options()) {
            applyOption(configuration, option);
        }
        // the classpath loader hides what the compiler must not resolve from the previous output
        try (HidingClassLoader hiding = new HidingClassLoader(toUrls(classpath), GroovyCompilation.class.getClassLoader(), hiddenClasses);
             GroovyClassLoader loader = new GroovyClassLoader(hiding, configuration)) {
            CompilationUnit unit = new CompilationUnit(configuration, null, loader);
            for (Path source : toCompile) {
                unit.addSource(source.toFile());
            }
            try {
                unit.compile();
            } catch (MultipleCompilationErrorsException e) {
                collect(e.getErrorCollector(), diagnostics);
                return null;
            }
            collect(unit.getErrorCollector(), diagnostics);
            return produced(unit);
        }
    }

    private static void applyOption(CompilerConfiguration configuration, String option) {
        // groovyc takes few options a build passes; the ones that matter to the output are honoured
        if (option.startsWith("--encoding=")) {
            configuration.setSourceEncoding(option.substring("--encoding=".length()));
        } else if ("--no-parameters".equals(option)) {
            configuration.setParameters(false);
        } else if (option.startsWith("--script-base-class=")) {
            configuration.setScriptBaseClass(option.substring("--script-base-class=".length()));
        }
    }

    private static StagedSourceCompiler.Produced produced(CompilationUnit unit) {
        Map<Path, Set<String>> classes = new HashMap<>();
        Iterator<SourceUnit> sources = unit.iterator();
        while (sources.hasNext()) {
            SourceUnit sourceUnit = sources.next();
            ModuleNode module = sourceUnit.getAST();
            if (module == null) {
                continue;
            }
            Path source = Path.of(sourceUnit.getName()).toAbsolutePath().normalize();
            Set<String> names = classes.computeIfAbsent(source, s -> new LinkedHashSet<>());
            for (ClassNode node : module.getClasses()) {
                // the transformations add the generated companions to the module, so they are credited too
                names.add(ClassDependencyIndex.topLevelOf(node.getName()));
            }
        }
        return new StagedSourceCompiler.Produced(classes, Map.of(), Map.of());
    }

    private static void collect(ErrorCollector collector, List<CompileDiagnostic> diagnostics) {
        if (collector.getWarnings() != null) {
            for (WarningMessage warning : collector.getWarnings()) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.WARNING, warning.getMessage(), null, 0, 0));
            }
        }
        if (collector.getErrors() == null) {
            return;
        }
        for (Message message : collector.getErrors()) {
            if (message instanceof SyntaxErrorMessage syntaxError) {
                SyntaxException cause = syntaxError.getCause();
                Path file = cause.getSourceLocator() != null ? Path.of(cause.getSourceLocator()) : null;
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, cause.getOriginalMessage(), file, cause.getLine(), cause.getStartColumn()));
            } else if (message instanceof ExceptionMessage exceptionMessage) {
                Throwable cause = exceptionMessage.getCause();
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, String.valueOf(cause.getMessage()), null, 0, 0));
            } else if (message instanceof SimpleMessage simpleMessage) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, simpleMessage.getMessage(), null, 0, 0));
            } else {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, message.toString(), null, 0, 0));
            }
        }
    }

    private static URL[] toUrls(List<String> classpath) {
        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = Path.of(classpath.get(i)).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Not a classpath entry: " + classpath.get(i), e);
            }
        }
        return urls;
    }

    /**
     * The compile classpath, minus the classes the compiler must not see: a class a deleted source
     * declared, or the previous output of a source being compiled, is not found, so a dependent fails
     * to compile rather than linking against a class about to go.
     */
    private static final class HidingClassLoader extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }

        private final Set<String> hiddenTopLevelClasses;

        HidingClassLoader(URL[] urls, @Nullable ClassLoader parent, Set<String> hiddenTopLevelClasses) {
            super(urls, parent);
            this.hiddenTopLevelClasses = hiddenTopLevelClasses;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!hiddenTopLevelClasses.isEmpty() && hiddenTopLevelClasses.contains(ClassDependencyIndex.topLevelOf(name))) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        @Nullable
        public URL getResource(String name) {
            if (name.endsWith(".class") && !hiddenTopLevelClasses.isEmpty()) {
                String binaryName = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                if (hiddenTopLevelClasses.contains(ClassDependencyIndex.topLevelOf(binaryName))) {
                    return null;
                }
            }
            return super.getResource(name);
        }
    }
}
