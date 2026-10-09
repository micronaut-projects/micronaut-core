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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Host files and system time zones under the Java POSIX backend of the virtual filesystem.
 */
final class GraalPyHostFileSystemTest {
    private static Context context;

    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() throws IOException {
        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
        context = GraalPyContextFactory.bootstrapReusableContext(GraalPyHostFileSystemTest.class.getClassLoader());
    }

    @AfterAll
    static void close() {
        try {
            context.close(true);
        } finally {
            PythonContextRuntime.setReuseContext(false);
            PythonContextRuntime.resetContext();
        }
    }

    private Value python(String function) {
        return context.eval(PYTHON, function);
    }

    @Test
    void replacesAHostFileInTheSameDirectory() throws IOException {
        Path source = Files.writeString(directory.resolve("a"), "new");
        Path target = Files.writeString(directory.resolve("b"), "old");

        python("lambda a, b: __import__('os').replace(a, b)").executeVoid(source.toString(), target.toString());

        assertFalse(Files.exists(source));
        assertEquals("new", Files.readString(target));
    }

    @Test
    void renamesAHostFileAndADirectory() throws IOException {
        Path file = Files.writeString(directory.resolve("file"), "x");
        Path folder = Files.createDirectory(directory.resolve("folder"));
        Files.writeString(folder.resolve("child"), "y");

        python("lambda a, b: __import__('os').rename(a, b)").executeVoid(file.toString(), directory.resolve("renamed").toString());
        python("lambda a, b: __import__('os').rename(a, b)").executeVoid(folder.toString(), directory.resolve("moved").toString());

        assertEquals("x", Files.readString(directory.resolve("renamed")));
        assertEquals("y", Files.readString(directory.resolve("moved/child")));
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(folder));
    }

    @Test
    void renamesRelativeToTheWorkingDirectory() throws IOException {
        Files.writeString(directory.resolve("relative"), "z");

        python("""
            def rename_in(directory):
                import os
                previous = os.getcwd()
                os.chdir(directory)
                try:
                    os.replace('relative', 'renamed')
                finally:
                    os.chdir(previous)
            rename_in
            """).executeVoid(directory.toString());

        assertEquals("z", Files.readString(directory.resolve("renamed")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theVirtualFileSystemStaysReadOnly() {
        Path target = directory.resolve("moved.py");
        String source = python("'/graalpy_vfs/src/' + sorted(__import__('os').listdir('/graalpy_vfs/src'))[0]").asString();

        PolyglotException failure = assertThrows(PolyglotException.class, () ->
            python("lambda a, b: __import__('os').replace(a, b)").executeVoid(source, target.toString()));

        assertTrue(failure.getMessage().contains("Error"), failure.getMessage());
        assertFalse(Files.exists(target));
    }

    @Test
    void movesWithoutAtomicityAcrossFileStores() throws IOException {
        List<List<CopyOption>> attempts = new ArrayList<>();
        FileSystem defaultFileSystem = FileSystem.newDefaultFileSystem();
        FileSystem host = new ForwardingMoveFileSystem(defaultFileSystem) {
            @Override
            public void move(Path source, Path target, CopyOption... options) throws IOException {
                attempts.add(List.of(options));
                if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "another file store");
                }
                defaultFileSystem.move(source, target, options);
            }
        };
        var fileSystem = new HostMoveFileSystem(defaultFileSystem, host, defaultFileSystem.parsePath("/graalpy_vfs"));
        Path source = Files.writeString(directory.resolve("a"), "new");
        Path target = Files.writeString(directory.resolve("b"), "old");

        fileSystem.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        assertEquals(List.of(
            List.of(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE),
            List.<CopyOption>of(StandardCopyOption.REPLACE_EXISTING)), attempts);
        assertEquals("new", Files.readString(target));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void zoneinfoSearchesTheSystemTimeZones() {
        assumeTrue(System.getenv(GraalPyContextFactory.PYTHON_TZ_PATH) == null, "the host configures PYTHONTZPATH");

        assertEquals(GraalPyContextFactory.SYSTEM_TZ_PATH,
            python("list(__import__('zoneinfo').TZPATH)").as(List.class));
        assumeTrue(GraalPyContextFactory.SYSTEM_TZ_PATH.stream()
            .anyMatch(location -> Files.isRegularFile(Path.of(location, "Europe", "Berlin"))), "no system time zone database");
        assertEquals("CEST", python("""
            import datetime, zoneinfo
            datetime.datetime(2026, 7, 1, tzinfo=zoneinfo.ZoneInfo('Europe/Berlin')).tzname()
            """).asString());
    }

    @Test
    void theDefaultTimeZonePathYieldsToConfiguration() {
        assertEquals(String.join(java.io.File.pathSeparator, GraalPyContextFactory.SYSTEM_TZ_PATH),
            GraalPyContextFactory.defaultTzPath(Map.of(), Map.of(), "Linux").orElseThrow());
        assertTrue(GraalPyContextFactory.defaultTzPath(Map.of("PYTHONTZPATH", "/opt/zoneinfo"), Map.of(), "Linux").isEmpty());
        assertTrue(GraalPyContextFactory.defaultTzPath(Map.of(), Map.of("PYTHONTZPATH", ""), "Mac OS X").isEmpty());
        assertTrue(GraalPyContextFactory.defaultTzPath(Map.of(), Map.of(), "Windows 11").isEmpty());
    }

    /** Forwards everything but {@code move}, which a test overrides. */
    private abstract static class ForwardingMoveFileSystem implements FileSystem {
        private final FileSystem delegate;

        ForwardingMoveFileSystem(FileSystem delegate) {
            this.delegate = delegate;
        }

        @Override
        public Path parsePath(java.net.URI uri) {
            return delegate.parsePath(uri);
        }

        @Override
        public Path parsePath(String path) {
            return delegate.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, java.util.Set<? extends java.nio.file.AccessMode> modes, java.nio.file.LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, java.nio.file.attribute.FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(dir, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(path);
        }

        @Override
        public java.nio.channels.SeekableByteChannel newByteChannel(Path path, java.util.Set<? extends java.nio.file.OpenOption> options, java.nio.file.attribute.FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(path, options, attrs);
        }

        @Override
        public java.nio.file.DirectoryStream<Path> newDirectoryStream(Path dir, java.nio.file.DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegate.newDirectoryStream(dir, filter);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegate.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, java.nio.file.LinkOption... linkOptions) throws IOException {
            return delegate.toRealPath(path, linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, java.nio.file.LinkOption... options) throws IOException {
            return delegate.readAttributes(path, attributes, options);
        }
    }
}
