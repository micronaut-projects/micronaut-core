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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Owns the initialized GraalPy processing context across multiple serialized compilations.
 *
 * <p>A session is not thread safe. Callers must serialize compilations that share it.</p>
 *
 * @since 5.2.0
 */
@Experimental
public final class PythonProcessingSession implements AutoCloseable {
    private PythonAstParser parser;
    private ClassLoader classLoader;
    private List<String> classLoaderFingerprint;
    private boolean closed;

    /**
     * Obtains the annotation processor class loader associated with this processing session.
     *
     * <p>The GraalPy context retains its host class loader. Reusing the context while creating a
     * different processor class loader for every compilation makes annotation types loaded by
     * Python incompatible with the visitors loaded for the current compilation. The class loader
     * must therefore have the same lifetime as the context.</p>
     *
     * @param classpath The effective annotation processor classpath
     * @param factory Creates the class loader when the classpath changes
     * @return The session class loader
     */
    @Internal
    public ClassLoader classLoader(List<File> classpath, Supplier<ClassLoader> factory) {
        if (closed) {
            throw new IllegalStateException("Python processing session is closed");
        }
        List<String> fingerprint = fingerprint(classpath);
        if (classLoader != null && fingerprint.equals(classLoaderFingerprint)) {
            return classLoader;
        }
        closeResources();
        classLoader = factory.get();
        classLoaderFingerprint = fingerprint;
        return classLoader;
    }

    /**
     * Obtains the parser for a compilation, initializing GraalPy on first use.
     *
     * @param classLoader The annotation processor class loader
     * @param incremental Whether the compilation is incremental
     * @return The session parser
     */
    @Internal
    public PythonAstParser parser(ClassLoader classLoader, boolean incremental) {
        if (closed) {
            throw new IllegalStateException("Python processing session is closed");
        }
        if (parser == null) {
            parser = new PythonAstParser(classLoader, incremental);
        }
        return parser;
    }

    /**
     * Reports whether GraalPy has been initialized.
     *
     * @return Whether GraalPy has been initialized for this session
     */
    public boolean initialized() {
        return parser != null;
    }

    @Override
    public void close() {
        closed = true;
        closeResources();
    }

    private void closeResources() {
        if (parser != null) {
            parser.close();
            parser = null;
        }
        if (classLoader instanceof URLClassLoader urlClassLoader
            && classLoader != PythonProcessingSession.class.getClassLoader()) {
            try {
                urlClassLoader.close();
            } catch (IOException ignored) {
                // Nothing useful can be done while releasing a compiler cache.
            }
        }
        classLoader = null;
        classLoaderFingerprint = null;
    }

    private static List<String> fingerprint(List<File> classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return List.of();
        }
        List<String> fingerprint = new ArrayList<>(classpath.size() * 2);
        for (File entry : classpath) {
            var path = entry.toPath().toAbsolutePath().normalize();
            fingerprint.add(path.toString());
            try {
                fingerprint.add(fingerprint(path));
            } catch (IOException e) {
                fingerprint.add("missing");
            }
        }
        return List.copyOf(fingerprint);
    }

    private static String fingerprint(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.walk(path)) {
                for (Path entry : entries.sorted().toList()) {
                    update(digest, path.relativize(entry).toString());
                    update(digest, Files.isDirectory(entry) ? "directory" : "file");
                    update(digest, Long.toString(Files.size(entry)));
                    update(digest, Long.toString(Files.getLastModifiedTime(entry).toMillis()));
                }
            }
        } else {
            update(digest, Long.toString(Files.size(path)));
            update(digest, Long.toString(Files.getLastModifiedTime(path).toMillis()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
