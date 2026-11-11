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
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
    private static final String OPENAPI_UTILS = "io.micronaut.openapi.visitor.Utils";

    private PythonAstParser parser;
    private ClassLoader classLoader;
    private List<String> classLoaderFingerprint;
    private @Nullable Runnable processorStateRelease;
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
     * Ends a compilation that ran with the session's class loader. The processors it loaded stay for the next
     * compilation with what they keep in static fields, so that state is released here, as a new class loader would
     * start it. A compilation that ended with a failure of a processor rather than a problem in the sources leaves
     * nothing that can be trusted, and the next one starts over.
     *
     * @param completed Whether the compiler ran to the end, with or without errors in the sources
     */
    @Internal
    public void compilationFinished(boolean completed) {
        if (classLoader == null) {
            return;
        }
        if (completed) {
            try {
                releaseProcessorState();
                return;
            } catch (RuntimeException | LinkageError e) {
                // the state stays: the class loader holding it goes
            }
        }
        closeResources();
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

    private void releaseProcessorState() {
        if (processorStateRelease == null) {
            processorStateRelease = processorStateRelease(classLoader);
        }
        processorStateRelease.run();
    }

    /**
     * What clears the static state of the OpenAPI visitor, the processor known to keep one, which holds the elements
     * of the last compilation, with their compiler, and the document it wrote, which the next compilation would add to.
     * The processor is optional, so it is looked up by name.
     */
    private static Runnable processorStateRelease(ClassLoader classLoader) {
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            // a native image holds the visitor, is not set up for a reflective lookup of it, and its launcher releases it
            return () -> { };
        }
        try {
            MethodHandle clean = MethodHandles.publicLookup().findStatic(
                Class.forName(OPENAPI_UTILS, false, classLoader), "clean", MethodType.methodType(void.class));
            return () -> {
                try {
                    clean.invokeExact();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | LinkageError e) {
            return () -> { };
        }
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
        processorStateRelease = null;
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
            // the files only: a directory rebuilt with the same files, as the classes another tool wrote into a
            // shared class output are for every compilation, holds what it held
            try (Stream<Path> entries = Files.walk(path)) {
                for (Path entry : entries.filter(Files::isRegularFile).sorted().toList()) {
                    update(digest, path.relativize(entry).toString());
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
