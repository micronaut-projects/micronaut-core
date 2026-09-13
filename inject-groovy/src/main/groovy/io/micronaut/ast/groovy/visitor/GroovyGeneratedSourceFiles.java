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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.writer.GeneratedFile;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The source files a {@link io.micronaut.inject.visitor.TypeElementVisitor} generates during a Groovy compilation.
 *
 * <p>The Groovy compiler has no annotation-processing rounds, so a source file written to disk while it runs is
 * never compiled. The compiler does, however, let a source be queued into the running
 * {@link CompilationUnit} ({@link CompilationUnit#addSource(String, String)}): once the phase that queued it
 * has finished, the unit rewinds to {@link Phases#INITIALIZATION} and brings the queued source up to the
 * current phase, skipping every source already through it. The resolver itself relies on this when it finds
 * a referenced class as a script on the class path.</p>
 *
 * <p>The rewind only skips the sources whose phase is marked complete, and the compiler marks a phase complete
 * after it has checked the queue. A source queued from the middle of a phase would therefore make the whole
 * phase run a second time for every source already in it, transforms included. So the generated sources are
 * not queued where they are written. Instead a phase operation is registered for the following phase; the
 * compiler appends it after every other operation of that phase, and when it runs it marks the phase complete
 * for the sources in the unit exactly as the compiler would, and only then queues the generated sources. The
 * rewind then processes the generated sources alone up to that phase, after which all sources continue
 * together. Micronaut's transforms, and every other global transform, see the generated class exactly once,
 * as they would a class the compilation started with, and its classes reach the same output as the others.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class GroovyGeneratedSourceFiles {

    private static final String EXTENSION = ".groovy";

    private final CompilationUnit compilationUnit;
    private final Map<String, GroovyGeneratedSourceFile> files = CollectionUtils.newLinkedHashMap(8);
    private final List<GroovyGeneratedSourceFile> pending = new ArrayList<>(4);
    private final Set<Integer> queuePhases = CollectionUtils.newHashSet(4);

    private GroovyGeneratedSourceFiles(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    /**
     * The generated source files of a compilation unit.
     *
     * @param compilationUnit The compilation unit
     * @return The generated source files of the unit, created on first use
     */
    static GroovyGeneratedSourceFiles of(CompilationUnit compilationUnit) {
        return compilationUnit.getAST().getNodeMetaData(GroovyGeneratedSourceFiles.class, key -> new GroovyGeneratedSourceFiles(compilationUnit));
    }

    /**
     * Visit a Groovy source file that will be compiled as part of the running compilation.
     *
     * @param packageName              The package of the source file
     * @param fileNameWithoutExtension The name of the source file, without extension
     * @return The file, or empty once the compilation is generating classes and nothing would process the source
     */
    Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension) {
        if (compilationUnit.getPhase() >= Phases.CLASS_GENERATION) {
            return Optional.empty();
        }
        String path = (packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/") + fileNameWithoutExtension + EXTENSION;
        return Optional.of(files.computeIfAbsent(path, GroovyGeneratedSourceFile::new));
    }

    private void written(GroovyGeneratedSourceFile file) throws IOException {
        int phase = compilationUnit.getPhase();
        if (phase >= Phases.CLASS_GENERATION) {
            throw new IOException("Generated source file [" + file.getName() + "] was written during " + compilationUnit.getPhaseDescription()
                + " of the Groovy compilation, when nothing would compile it: generated sources must be written before class generation");
        }
        if (!pending.contains(file)) {
            pending.add(file);
        }
        int queuePhase = phase + 1;
        if (queuePhases.add(queuePhase)) {
            compilationUnit.addNewPhaseOperation(this::queuePending, queuePhase);
        }
    }

    private void queuePending(SourceUnit source) {
        if (pending.isEmpty()) {
            return;
        }
        // Every other operation of this phase has run for every source in the unit: mark the phase complete for
        // them, as CompilationUnit.mark() would once the queue was found empty, so the rewind that brings in the
        // generated sources does not run this phase again for the sources already through it
        int phase = compilationUnit.getPhase();
        Iterator<SourceUnit> sources = compilationUnit.iterator();
        while (sources.hasNext()) {
            SourceUnit existing = sources.next();
            if (existing.getPhase() < phase) {
                existing.gotoPhase(phase);
            }
            if (existing.getPhase() == phase && !existing.isPhaseComplete()) {
                existing.completePhase();
            }
        }
        for (GroovyGeneratedSourceFile file : pending) {
            compilationUnit.addSource(file.getName(), file.text);
            file.queued = true;
        }
        pending.clear();
    }

    /**
     * An in-memory generated source file, handed to the compiler once it is written and closed.
     */
    private final class GroovyGeneratedSourceFile implements GeneratedFile {

        private final String path;
        private String text = "";
        private boolean queued;

        private GroovyGeneratedSourceFile(String path) {
            this.path = path;
        }

        @Override
        public URI toURI() {
            return URI.create("memory:" + path);
        }

        @Override
        public String getName() {
            return path;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            checkNotQueued();
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    text = toString(StandardCharsets.UTF_8);
                    written(GroovyGeneratedSourceFile.this);
                }
            };
        }

        @Override
        public Reader openReader() {
            return new StringReader(text);
        }

        @Override
        public CharSequence getTextContent() {
            return text;
        }

        @Override
        public Writer openWriter() throws IOException {
            checkNotQueued();
            return new StringWriter() {
                @Override
                public void close() throws IOException {
                    text = toString();
                    written(GroovyGeneratedSourceFile.this);
                }
            };
        }

        private void checkNotQueued() throws IOException {
            if (queued) {
                throw new IOException("Generated source file [" + path + "] has already been handed to the Groovy compiler and cannot be written again");
            }
        }
    }
}
