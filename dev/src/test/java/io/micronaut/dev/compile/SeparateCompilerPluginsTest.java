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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build plugin launches a project with its runtime and the compiler, while what runs inside the compiler is on the
 * compile classpath or the processor path alone: {@code micronaut-inject} is then on both, and the classes that run
 * inside the compiler must see the copy their own loader has, whose package-private members they reach.
 */
class SeparateCompilerPluginsTest {

    /**
     * What a project's launch classpath does not have: what runs inside the compilers, and what that alone depends on.
     */
    private static final List<String> COMPILER_PLUGINS = List.of("micronaut-core-processor", "micronaut-inject-groovy", "micronaut-inject-java", "micronaut-inject-kotlin", "micronaut-sourcegen");

    private static final String BEAN_DEFINITION = "example/$Service$Definition.class";

    @TempDir
    Path project;

    @Test
    void groovyFindsTheTransformationsOnTheCompileClasspath() throws Exception {
        Path source = project.resolve("src/example/Service.groovy");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example
            @jakarta.inject.Singleton
            @io.micronaut.context.annotation.Requires(property = 'some.property', defaultValue = 'value')
            class Service { }
            """);

        compile(SourceKind.GROOVY);

        assertTrue(Files.exists(project.resolve("classes").resolve(BEAN_DEFINITION)));
    }

    @Test
    void kspFindsTheSymbolProcessorsOnTheProcessorPath() throws Exception {
        Path source = project.resolve("src/example/Service.kt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example
            @jakarta.inject.Singleton
            @io.micronaut.context.annotation.Requires(property = "some.property", defaultValue = "value")
            class Service
            """);

        compile(SourceKind.KOTLIN);

        assertTrue(Files.exists(project.resolve("classes").resolve(BEAN_DEFINITION)));
    }

    private void compile(SourceKind kind) throws Exception {
        List<String> classpath = Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator));
        Path argfile = project.resolve("jvm.argfile");
        String launchClasspath = String.join(File.pathSeparator, classpath.stream().filter(entry -> !isCompilerPlugin(entry)).toList());
        Files.write(argfile, List.of("-cp", "\"" + launchClasspath.replace("\\", "\\\\") + "\""));
        // the project's classpaths: the test classes on them would register a test's symbol processor
        String projectClasspath = String.join(File.pathSeparator, classpath.stream().filter(entry -> !Files.isDirectory(Path.of(entry))).toList());
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + argfile, Compile.class.getName(), kind.name(), project.toString(), projectClasspath)
            .redirectErrorStream(true)
            .start();
        String[] output = new String[1];
        Thread reader = new Thread(() -> {
            try {
                output[0] = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                output[0] = e.toString();
            }
        });
        reader.start();
        boolean exited = process.waitFor(4, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        assertTrue(exited, "the compilation did not finish:\n" + output[0]);
        assertEquals(0, process.exitValue(), output[0]);
    }

    private static boolean isCompilerPlugin(String entry) {
        String fileName = Path.of(entry).getFileName().toString();
        return COMPILER_PLUGINS.stream().anyMatch(fileName::startsWith);
    }

    /**
     * Runs one compilation on the launch classpath it is started with.
     */
    static final class Compile {

        public static void main(String[] args) {
            try {
                SourceKind kind = SourceKind.valueOf(args[0]);
                Path project = Path.of(args[1]);
                List<Path> classpath = Arrays.stream(args[2].split(File.pathSeparator)).map(Path::of).toList();
                SourceCompiler compiler = kind == SourceKind.GROOVY ? new GroovySourceCompiler() : new KotlinSourceCompiler();
                // the Groovy transformations are found on the compile classpath, the symbol processors on the processor path
                List<Path> processorPath = kind == SourceKind.GROOVY ? List.of() : classpath;
                CompilationRequest request = new CompilationRequest(kind, List.of(new SourceRoot(kind, project.resolve("src"))), Set.of(), Set.of(), true, classpath, processorPath,
                    project.resolve("classes"), project.resolve("generated"), List.of());
                CompilationResult result = compiler.compile(request);
                System.out.println(result.status() + " " + result.diagnostics());
                System.exit(result.status() == CompilationResult.Status.SUCCESS ? 0 : 1);
            } catch (Throwable e) {
                // the threads of a compiler that failed would keep the JVM alive
                e.printStackTrace();
                System.exit(2);
            }
        }
    }
}
