/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.python.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.PrintWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "pyronautc", mixinStandardHelpOptions = true, description = "Compiles a Pyronaut application")
public class PyronautCliCompiler implements Callable<Integer> {
    @Option(names = {"-s",
        "--source-directory"}, description = "Directory where to look for Python sources",
        required = true)
    public File sourceDirectory;

    @Option(names = {"-o",
        "--output-directory"}, description = "Directory where to write generated classes",
        required = true)
    public File outputDirectory;

    @Option(names = {"-processorpath",
        "--processor-path"}, description = "Annotation processor classpath", split = "${sys:path.separator}")
    public List<File> annotationProcessorPath;

    @Option(names = {"-cp",
        "--classpath"}, description = "Compilation classpath", split = "${sys:path.separator}")
    public List<File> classpath;

    @Option(names = {"-bootclasspath",
        "--boot-class-path"}, description = "Boot classpath", split = "${sys:path.separator}")
    public List<File> bootclasspath;

    @Option(names = {"-option", "--option"}, description = "Additional compiler options")
    public List<String> options;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    public boolean verbose = false;

    public ClassLoader classLoader;

    @Override
    public Integer call() throws Exception {
        Files.createDirectories(outputDirectory.toPath());
        try {
            var compiler = ToolProvider.getSystemJavaCompiler();
            var diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
            var fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);
            // Set the class output location
            try {
                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            } catch (Exception e) {
                throw new RuntimeException("Failed to set output location", e);
            }
            compileJava(
                classLoader,
                compiler,
                classpath,
                List.of(),
                annotationProcessorPath,
                options,
                fileManager,
                diagnosticCollector
            );
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            return -1;
        }
        return 0;
    }

    private void compileJava(ClassLoader parentClassLoader,
                             JavaCompiler compiler,
                             List<File> classpath,
                             List<File> bootclasspath,
                             List<File> annotationProcessorPath,
                             List<String> compilerOptions,
                             JavaFileManager fileManager,
                             DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        var options = buildCompilerOptions(classpath, bootclasspath, annotationProcessorPath, compilerOptions);
        if (verbose) {
            System.out.println("Compiler options:");
            for (var option : options) {
                System.out.println("   "  + option);
            }
        }
        var task = compiler.getTask(
            new PrintWriter(System.out),
            new ForwardingJavaFileManager<>(fileManager) {
                @Override
                public ClassLoader getClassLoader(Location location) {
                    var classLoader = super.getClassLoader(location);
                    if (parentClassLoader!= null && classLoader instanceof URLClassLoader urlClassLoader) {
                        return new URLClassLoader(urlClassLoader.getURLs(), parentClassLoader);
                    }
                    return classLoader;
                }
            },
            diagnosticCollector,
            options,
            List.of(),
            List.of(new GeneratedEntryPoint(sourceDirectory.toPath()))
        );

        boolean success = task.call();
        if (!success) {
            throw new RuntimeException(
                "Compilation failed: " + diagnosticCollector.getDiagnostics());
        }
    }

    private List<String> buildCompilerOptions(List<File> classpath,
                                              List<File> bootClasspath,
                                              List<File> annotationProcessorPath,
                                              List<String> compilerOptions) {
        List<String> options = new ArrayList<>();
        addClasspathOption("-classpath", classpath, options);
        addClasspathOption("-bootclasspath", bootClasspath, options);
        addClasspathOption("-processorpath", annotationProcessorPath, options);
        if (compilerOptions != null) {
            options.addAll(compilerOptions);
        }
        return options;
    }

    private static void addClasspathOption(String option,
                                           List<File> classpath,
                                           List<String> options) {
        if (classpath == null || classpath.isEmpty()) {
            return;
        }
        options.add(option);
        var cp = new StringBuilder();
        for (var file : classpath) {
            if (!cp.isEmpty()) {
                cp.append(File.pathSeparator);
            }
            cp.append(file.getAbsolutePath());
        }
        var classpathString = cp.toString();
        options.add(classpathString);
    }

    public static void main(String[] args) {
        var exitCode = new CommandLine(new PyronautCliCompiler())
            .execute(args);
        System.exit(exitCode);
    }
}
