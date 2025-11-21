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

import java.io.File;
import java.nio.file.Files;
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PyronautCliCompiler())
            .execute(args);
        System.exit(exitCode);
    }
}
