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
package io.micronaut.python.compiler;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces GraalPy-compatible, hash-based Python bytecode without requiring a Python launcher
 * or a writable Python filesystem.
 *
 * @author Micronaut
 * @since 5.2.0
 */
@Internal
public final class PythonBytecodeCompiler implements AutoCloseable {
    private static final String PYTHON = "python";
    private static final Source COMPILE_SOURCE = Source.newBuilder(PYTHON, """
        import importlib.util as _mn_importlib_util
        import marshal as _mn_marshal
        import struct as _mn_struct

        def _mn_compile_python_bytecode(source, filename):
            source_bytes = source.encode('utf-8')
            code = compile(source, filename, 'exec')
            # PEP 552 checked-hash header: hash-based flag plus source checking.
            header = (
                _mn_importlib_util.MAGIC_NUMBER
                + _mn_struct.pack('<I', 0x03)
                + _mn_importlib_util.source_hash(source_bytes)
            )
            return (
                _mn_importlib_util.cache_from_source(filename),
                header + _mn_marshal.dumps(code)
            )
        """, "micronaut-bytecode-compiler.py").cached(true).buildLiteral();

    private final Context context;
    private final Value compiler;
    private final boolean ownsContext;

    /**
     * Create an embedded GraalPy bytecode compiler.
     */
    public PythonBytecodeCompiler() {
        this(Context.newBuilder(PYTHON).allowAllAccess(true).build(), true);
    }

    /**
     * Create a bytecode compiler that reuses an existing GraalPy context.
     * Closing this compiler does not close the supplied context.
     *
     * @param context The existing context
     */
    public PythonBytecodeCompiler(Context context) {
        this(context, false);
    }

    private PythonBytecodeCompiler(Context context, boolean ownsContext) {
        this.context = context;
        this.ownsContext = ownsContext;
        context.eval(COMPILE_SOURCE);
        compiler = context.getBindings(PYTHON).getMember("_mn_compile_python_bytecode");
    }

    /**
     * Compile source text to a checked-hash {@code .pyc} payload.
     *
     * @param source The Python source text
     * @param filename The logical source filename used for bytecode cache naming and tracebacks
     * @return The cache filename selected by GraalPy and its bytecode bytes
     */
    public Result compile(String source, String filename) {
        Value result = compiler.execute(source, filename);
        return new Result(
            result.getArrayElement(0).asString(),
            result.getArrayElement(1).as(byte[].class)
        );
    }

    /**
     * Compile all Python source entries listed by a GraalPy VFS file list.
     *
     * @param args The VFS resource root followed by the relative file list path
     * @throws IOException If an input or output resource cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected arguments: <vfs-resource-root> <fileslist-path>");
        }
        compileVfsResources(Path.of(args[0]), Path.of(args[1]));
    }

    /**
     * Compile Python resources listed in a VFS {@code fileslist.txt} and append the cache entries.
     *
     * @param resourceRoot The physical root of the processed VFS resource directory
     * @param filesListPath The file list path relative to {@code resourceRoot}
     * @throws IOException If an input or output resource cannot be read or written
     */
    public static void compileVfsResources(Path resourceRoot, Path filesListPath) throws IOException {
        Path fileList = resourceRoot.resolve(filesListPath);
        List<String> entries = Files.readAllLines(fileList, StandardCharsets.UTF_8);
        List<String> generatedEntries = new ArrayList<>();
        try (PythonBytecodeCompiler compiler = new PythonBytecodeCompiler()) {
            for (String entry : entries) {
                int sourceIndex = entry.indexOf("/src/");
                if (sourceIndex == -1 || !entry.endsWith(".py")) {
                    continue;
                }
                String relativeSource = entry.substring(sourceIndex + 1);
                Path source = resourceRoot.resolve(relativeSource);
                if (!Files.isRegularFile(source)) {
                    throw new IOException("VFS file list entry does not exist: " + entry);
                }
                Result result = compiler.compile(Files.readString(source, StandardCharsets.UTF_8), entry);
                String cacheFileName = Path.of(result.cachePath()).getFileName().toString();
                Path cache = source.getParent().resolve("__pycache__").resolve(cacheFileName);
                Files.createDirectories(cache.getParent());
                Files.write(cache, result.bytes());
                String cacheEntry = entry.substring(0, entry.lastIndexOf('/') + 1)
                    + "__pycache__/" + cacheFileName;
                if (!entries.contains(cacheEntry) && !generatedEntries.contains(cacheEntry)) {
                    generatedEntries.add(cacheEntry);
                }
            }
        }
        if (!generatedEntries.isEmpty()) {
            List<String> updated = new ArrayList<>(entries);
            updated.addAll(generatedEntries);
            Files.write(fileList, updated, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        if (ownsContext) {
            context.close();
        }
    }

    /**
     * A generated bytecode payload.
     *
     * @param cachePath The cache filename calculated by GraalPy
     * @param bytes The complete {@code .pyc} file bytes
     */
    @SuppressWarnings("ArrayRecordComponent")
    public record Result(String cachePath, byte[] bytes) {
    }
}
