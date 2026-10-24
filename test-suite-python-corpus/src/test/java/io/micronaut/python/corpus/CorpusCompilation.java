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

import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Compiles applications of the corpus in a JVM of its own, as a build does: the arguments are
 * triples of the corpus name (which names the system properties holding its class paths), its
 * source directory and the output directory, and an optional leading {@code --mode=<mode>}.
 * Prints the wall time of each compilation.
 */
public final class CorpusCompilation {

    private CorpusCompilation() {
    }

    /**
     * @param args The arguments
     */
    public static void main(String[] args) {
        int first = 0;
        StaticCompilationMode mode = StaticCompilationMode.ALL;
        if (args.length > 0 && args[0].startsWith("--mode=")) {
            mode = StaticCompilationMode.fromOption(args[0].substring("--mode=".length()));
            first = 1;
        }
        for (int i = first; i + 2 < args.length; i += 3) {
            String corpus = args[i];
            File source = new File(args[i + 1]);
            File out = new File(args[i + 2]);
            File classes = new File(out, "classes");
            classes.mkdirs();
            List<String> diagnostics = new ArrayList<>();
            long started = System.nanoTime();
            PyronautCompiler.builder()
                .pythonSrc(source.getPath())
                .targetDir(classes)
                .classpath(files(System.getProperty("corpus." + corpus + ".classpath")))
                .annotationProcessorPath(files(System.getProperty("corpus." + corpus + ".processorPath")))
                .typeCheck(TypeCheckMode.ERROR)
                .staticCompilation(mode)
                .staticCompilationReport(new File(out, "report"))
                .pythonDiagnosticCallback(diagnostic -> diagnostics.add(diagnostic.toString()))
                .build()
                .compile();
            System.out.printf(Locale.ROOT, "compiled %s in mode %s: %.1f ms%n", corpus, mode.optionValue(), (System.nanoTime() - started) / 1_000_000.0);
            if (!diagnostics.isEmpty()) {
                throw new IllegalStateException("the type checker reports on " + corpus + ": " + diagnostics);
            }
        }
    }

    private static List<File> files(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator)).map(File::new).toList();
    }
}
