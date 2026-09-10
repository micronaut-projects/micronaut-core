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
package io.micronaut.annotation.processing.test.jdt;

import io.micronaut.annotation.processing.test.JavaParser;
import org.jspecify.annotations.NonNull;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A {@link JavaParser} that compiles with the Eclipse JDT compiler (ECJ) rather than javac.
 *
 * <p>The Micronaut annotation processors are written against the standard {@code javax.lang.model}
 * API, but the two compilers differ in the details they are permitted to differ in: the ordering of
 * {@code TypeElement#getEnclosedElements()}, the shape of synthesised record members, how
 * {@code TypeMirror}s of erased/annotated types are reported and so on. Compiling the same sources
 * with this parser and with {@link JavaParser} makes those differences visible.</p>
 *
 * <p>Only {@link #generate(JavaFileObject...)} is supported; the {@code parse}/{@code analyze}
 * phases of {@link JavaParser} rely on {@code com.sun.source} APIs that ECJ does not implement.</p>
 *
 * @since 5.2.0
 */
public class JdtParser extends JavaParser {

    private Path sourceDirectory;

    @Override
    protected @NonNull JavaCompiler createCompiler() {
        return new EclipseCompiler();
    }

    @Override
    protected Set<String> getCompilerOptions() {
        // NOTE: JavaParser models the options as a Set, so an option value that repeats
        // (for example "-source 25 -target 25") would be swallowed by de-duplication.
        // ECJ's single argument form of the compliance level avoids that.
        return new LinkedHashSet<>(List.of(
            "-proc:full",
            "-" + Runtime.version().feature(),
            "-nowarn"
        ));
    }

    @Override
    public Iterable<? extends Element> parse(JavaFileObject... sources) {
        throw new UnsupportedOperationException(
            "The JDT parser does not support the parse/analyze phases, only full compilation. Use generate(...)");
    }

    @Override
    public Iterable<? extends JavaFileObject> generate(JavaFileObject... sources) {
        List<Processor> processors = getAnnotationProcessors();
        JavaCompiler.CompilationTask task = getCompiler().getTask(
            null,
            getFileManager(),
            getDiagnosticCollector(),
            getCompilerOptions(),
            null,
            toFileBackedSources(sources)
        );
        task.setProcessors(processors);
        Boolean result;
        try {
            result = task.call();
        } finally {
            List<Diagnostic<? extends JavaFileObject>> diagnostics = getDiagnosticCollector().getDiagnostics();
            StringBuilder error = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    error.append(diagnostic);
                }
            }
            if (!error.isEmpty()) {
                throw new RuntimeException(error.toString());
            }
        }
        if (!Boolean.TRUE.equals(result)) {
            throw new RuntimeException("Compilation with the Eclipse JDT compiler failed");
        }
        List<JavaFileObject> files = new ArrayList<>();
        getOutputFiles().forEach(files::add);
        return files;
    }

    /**
     * ECJ turns each compilation unit into a command line argument and requires the corresponding
     * file to exist on disk, so the in-memory sources are written out to a temporary directory first.
     *
     * @param sources The in-memory sources
     * @return File backed equivalents
     */
    private List<JavaFileObject> toFileBackedSources(JavaFileObject... sources) {
        try {
            if (sourceDirectory == null) {
                sourceDirectory = Files.createTempDirectory("micronaut-jdt-sources");
                sourceDirectory.toFile().deleteOnExit();
            }
            List<JavaFileObject> result = new ArrayList<>(sources.length);
            for (JavaFileObject source : sources) {
                String relativePath = source.toUri().getPath();
                if (relativePath == null) {
                    relativePath = source.getName();
                }
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                Path file = sourceDirectory.resolve(relativePath);
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getCharContent(true).toString(), StandardCharsets.UTF_8);
                result.add(new FileBackedSource(file));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        super.close();
        if (sourceDirectory != null) {
            try (Stream<Path> walk = Files.walk(sourceDirectory)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                // ignore
            }
            sourceDirectory = null;
        }
    }

    /**
     * A source file object backed by a real file, as required by ECJ.
     */
    private static final class FileBackedSource extends SimpleJavaFileObject {
        private final Path path;

        private FileBackedSource(Path path) {
            super(path.toUri(), Kind.SOURCE);
            this.path = path;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return path.toString();
        }
    }
}
