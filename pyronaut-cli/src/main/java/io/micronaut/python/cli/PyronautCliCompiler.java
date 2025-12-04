/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli;

import io.micronaut.python.compiler.PyronautCompiler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "pyronautc", mixinStandardHelpOptions = true, description = "Compiles a Pyronaut application")
public class PyronautCliCompiler implements Callable<Integer> {
    @Option(names = {"-s",
        "--source-directory"}, description = "Directory where to look for Python sources",
        required = true)
    private File sourceDirectory;

    @Option(names = {"-o",
        "--output-directory"}, description = "Directory where to write generated classes",
        required = true)
    private File outputDirectory;

    @Option(names = {"-processorpath",
        "--processor-path"}, description = "Annotation processor classpath", split = "${sys:path.separator}")
    private List<File> annotationProcessorPath;

    @Option(names = {"-cp",
        "--classpath"}, description = "Compilation classpath", split = "${sys:path.separator}")
    private List<File> classpath;

    @Option(names = {"-bootclasspath",
        "--boot-class-path"}, description = "Boot classpath", split = "${sys:path.separator}")
    private List<File> bootclasspath;

    @Option(names = {"-option", "--option"}, description = "Additional compiler options")
    private List<String> options;

    @Override
    public Integer call() throws Exception {
        Files.createDirectories(outputDirectory.toPath());
        var compiler = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.getAbsolutePath())
            .classpath(classpath)
            .bootclasspath(bootclasspath)
            .annotationProcessorPath(annotationProcessorPath)
            .targetDir(outputDirectory)
            .options(options)
            .build();
        try {
            compiler.compile();
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            return -1;
        }
        return 0;
    }


    private void compileJava(JavaFileObject[] sources,
                             List<File> classpath,
                             List<File> bootclasspath,
                             List<File> annotationProcessorPath,
                             List<String> compilerOptions,
                             JavaFileManager fileManager,
                             DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        List<String> options = buildCompilerOptions(classpath, bootclasspath, annotationProcessorPath, compilerOptions);

        try {
            var compiler = ToolProvider.getSystemJavaCompiler();
            var task = compiler.getTask(
                null, // Writer for additional output
                fileManager, // File manager
                diagnosticCollector, // Diagnostic collector
                options, // Compiler options
                List.of(), // Classes to process (none)
                Arrays.asList(sources) // Source files
            );

            task.setProcessors(processors);

            boolean success = task.call();
            if (!success) {
                throw new RuntimeException(
                    "Compilation failed: " + diagnosticCollector.getDiagnostics());
            }
        } finally {
            shutdownProcessors(processors);
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
        for (File file : classpath) {
            if (!cp.isEmpty()) {
                cp.append(File.pathSeparator);
            }
            cp.append(file.getAbsolutePath());
        }
        var classpathString = cp.toString();
        options.add(classpathString);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PyronautCliCompiler())
            .execute(args);
        System.exit(exitCode);
    }
}
