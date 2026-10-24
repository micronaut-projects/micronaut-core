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
package io.micronaut.python.corpus;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The same sources compile to the same bytes: the classes and the Java sources generated for the
 * applications of the corpus in mode {@code all}, and the static compilation report. The two
 * compilations run in JVMs of their own, as two builds do, so what a JVM randomizes (the iteration
 * order of {@code Map.of} and {@code Set.of}, identity hash codes) differs between them, and the
 * second one writes the source files in the reverse order, so a file system listing them in the
 * order they were created lists them differently.
 */
class StaticCompilationReproducibilityTest {

    private static final List<String> CORPORA = List.of("fullstack", "petclinic");

    @Test
    void theSameSourcesCompileToTheSameBytes() throws Exception {
        Path root = Path.of("build/corpus-reproducible").toAbsolutePath();
        // the same paths both times: the report and the line maps of the stubs name the sources by their paths
        Path source = root.resolve("src");
        Path out = root.resolve("out");
        Map<String, byte[]> first = null;
        for (int run = 0; run < 2; run++) {
            deleteRecursively(source);
            deleteRecursively(out);
            List<String> arguments = new ArrayList<>();
            for (String corpus : CORPORA) {
                copy(Path.of("src/corpus", corpus), source.resolve(corpus), run == 1);
                arguments.addAll(List.of(corpus, source.resolve(corpus).toString(), out.resolve(corpus).toString()));
            }
            compileInAJvmOfItsOwn(root.resolve("run-" + run + ".log"), arguments);
            Map<String, byte[]> files = contents(out);
            if (run == 0) {
                // kept for a diff with the output of the second run, which stays in out
                deleteRecursively(root.resolve("first"));
                Files.move(out, root.resolve("first"));
            }
            assertTrue(files.keySet().stream().anyMatch(name -> name.endsWith("$PyronautCompiled.class")), "mode all compiles bodies");
            if (first == null) {
                first = files;
                continue;
            }
            assertEquals(new TreeSet<>(first.keySet()), new TreeSet<>(files.keySet()), "the same files are generated");
            List<String> differing = new ArrayList<>();
            for (Map.Entry<String, byte[]> file : first.entrySet()) {
                if (!Arrays.equals(file.getValue(), files.get(file.getKey()))) {
                    differing.add(file.getKey());
                }
            }
            if (!differing.isEmpty()) {
                fail("compiling the same sources again changed " + differing.size() + " files (diff " + root.resolve("first")
                    + " with " + out + "): " + differing);
            }
        }
    }

    private static void compileInAJvmOfItsOwn(Path log, List<String> arguments) throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx2g", "-cp", System.getProperty("java.class.path")));
        for (String corpus : CORPORA) {
            for (String kind : List.of("classpath", "processorPath")) {
                String property = "corpus." + corpus + "." + kind;
                command.add("-D" + property + "=" + System.getProperty(property, ""));
            }
        }
        command.add(CorpusCompilation.class.getName());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            fail("the compilation did not finish in 10 minutes, see " + log);
        }
        assertEquals(0, process.exitValue(), () -> "the compilation failed, see " + log + ":\n" + read(log));
    }

    private static String read(Path log) {
        try {
            return Files.readString(log, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.toString();
        }
    }

    /**
     * Copies a directory, creating its files and directories in the order of their paths or in the reverse order.
     */
    private static void copy(Path from, Path to, boolean reversed) throws IOException {
        List<Path> paths;
        try (Stream<Path> walked = Files.walk(from)) {
            paths = walked.sorted().toList();
        }
        Comparator<Path> order = reversed ? Comparator.<Path>naturalOrder().reversed() : Comparator.naturalOrder();
        for (Path path : paths.stream().filter(Files::isDirectory).toList()) {
            Files.createDirectories(to.resolve(from.relativize(path).toString()));
        }
        for (Path path : paths.stream().filter(Files::isRegularFile).sorted(order).toList()) {
            Files.copy(path, to.resolve(from.relativize(path).toString()));
        }
    }

    /**
     * @return The contents of every file under the directory, by the path relative to it
     */
    private static Map<String, byte[]> contents(Path directory) throws IOException {
        Map<String, byte[]> contents = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                contents.put(directory.relativize(path).toString().replace(File.separatorChar, '/'), Files.readAllBytes(path));
            }
        }
        return contents;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
