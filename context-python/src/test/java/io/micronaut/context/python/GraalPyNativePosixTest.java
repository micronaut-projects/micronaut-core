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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyNativePosixTest {
    private static final String UNIX_TRANSPORT = """
        import socket
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as server:
            server.settimeout(2)
            server.bind(socket_path)
            server.listen(1)
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
                client.settimeout(2)
                client.connect(socket_path)
                connection, _ = server.accept()
                with connection:
                    connection.settimeout(2)
                    client.sendall(b'posix')
                    payload = b''
                    while len(payload) < 5:
                        chunk = connection.recv(5 - len(payload))
                        assert chunk, 'connection closed before the whole payload arrived'
                        payload += chunk
                    assert payload == b'posix'
        """;

    @TempDir
    Path resources;

    @AfterEach
    @BeforeEach
    void resetRuntime() {
        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
    }

    @Test
    void defaultVirtualFileSystemKeepsJavaBackend() throws IOException {
        try (Context context = GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader())) {
            assertFalse(context.eval("python", "import socket; hasattr(socket, 'AF_UNIX')").asBoolean());
        }
    }

    @Test
    void externalResourcesKeepJavaBackend() throws IOException {
        extractResources(getClass().getClassLoader());
        Context context;
        try (ApplicationContext application = ApplicationContext.run(Map.of(
            "graalpy.context.resource-directory", resources.toString()
        ))) {
            assertEquals(resources, application.getBean(GraalPyContextConfiguration.class).getResourceDirectory());
            context = application.getBean(Context.class);
            assertFalse(context.eval("python", "import socket; hasattr(socket, 'AF_UNIX')").asBoolean());
        }
        PolyglotException closed = assertThrows(PolyglotException.class, () -> context.eval("python", "1"));
        assertTrue(closed.isCancelled());
        assertTrue(Files.isDirectory(resources.resolve("src")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativePosixConfigurationSupportsUnixTransport() throws IOException {
        var configuration = nativeConfiguration(true);
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = GraalPyContextFactory.buildContext(
                 GraalPyContextFactory.bootstrapHostAccess(getClass().getClassLoader()),
                 engine, getClass().getClassLoader(), configuration)) {
            exchange(context);
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativeExternalResourcesSupportUnixTransport() throws IOException {
        try (Context context = nativeExternalContext(true)) {
            exchange(context);
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativeExternalResourcesFailClosedWithoutNativeAccess() {
        PolyglotException failure = assertThrows(PolyglotException.class, () -> {
            try (Context context = nativeExternalContext(false)) {
                exchange(context);
            }
        });
        assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("native access"), failure.getMessage());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativeFactoryFailsClosedWithoutNativeAccess() throws IOException {
        var configuration = nativeConfiguration(false);
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> {
                try (Context context = buildContext(engine, configuration)) {
                    exchange(context);
                }
            });
            assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("native access"), failure.getMessage());
        }
    }

    @Test
    void nativeBackendRequiresPhysicalResources() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
            GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader(),
                Map.of("python.PosixModuleBackend", "native")));
        assertTrue(failure.getMessage().contains("resource-directory"), failure.getMessage());
        assertFalse(PythonContextRuntime.isInitialized());
    }

    @Test
    void invalidBackendIsNotSilentlyReplacedByJava() {
        PolyglotException failure = assertThrows(PolyglotException.class, () -> GraalPyContextFactory.bootstrapReusableContext(
            getClass().getClassLoader(), Map.of("python.PosixModuleBackend", "invalid")));
        assertTrue(failure.getMessage().contains("Wrong value for the PosixModuleBackend option"), failure.getMessage());
        assertFalse(PythonContextRuntime.isInitialized());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void applicationConfigurationBindsPhysicalResourcesToPrimaryAndPool() throws IOException {
        extractResources(getClass().getClassLoader());
        Context primary;
        try (ApplicationContext application = ApplicationContext.run(Map.of(
            "graalpy.context.resource-directory", resources.toString(),
            "graalpy.context.options.python.PosixModuleBackend", "native",
            "graalpy.context.allow-native-access", true,
            "graalpy.context.host-class-lookup", List.of("example.allowed"),
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            var configuration = application.getBean(GraalPyContextConfiguration.class);
            assertEquals(resources, configuration.getResourceDirectory());
            assertEquals("native", configuration.getOptions().get("python.PosixModuleBackend"));
            primary = application.getBean(Context.class);
            exchange(primary);
            assertThrows(PolyglotException.class, () -> primary.eval("python", "import java; java.type('org.junit.jupiter.api.Test')"));
            Files.delete(resources.resolve("unix.sock"));
            application.getBean(PythonContextExecutor.class).withContext(pooled -> {
                exchange(pooled);
                return null;
            });
        }
        PolyglotException closed = assertThrows(PolyglotException.class, () -> primary.eval("python", "1"));
        assertTrue(closed.isCancelled());
        assertTrue(Files.isDirectory(resources.resolve("src")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void applicationConfigurationDoesNotGrantNativeAccessByDefault() throws IOException {
        extractResources(getClass().getClassLoader());
        BeanInstantiationException failure = assertThrows(BeanInstantiationException.class, () -> {
            try (ApplicationContext application = ApplicationContext.run(Map.of(
                "graalpy.context.resource-directory", resources.toString(),
                "graalpy.context.options.python.PosixModuleBackend", "native"
            ))) {
                application.getBean(Context.class);
            }
        });
        assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("native access"), failure.getMessage());
        assertFalse(PythonContextRuntime.isInitialized());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void reusableBootstrapHonorsExternalResourcesAndPermissions() throws IOException {
        try (Context context = GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader(),
            nativeConfiguration(true), GraalPyContextFactory.APPLICATION_MAIN)) {
            exchange(context);
            assertTrue(Files.isDirectory(resources.resolve("src")));
            assertSame(context, GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader(),
                nativeConfigurationWithoutExtraction(true), GraalPyContextFactory.APPLICATION_MAIN));
            assertSame(context, GraalPyContextFactory.bootstrapReusableContext(
                getClass().getClassLoader(), Map.of("python.PosixModuleBackend", "java")));
        }
        // External resources belong to the caller, not the closed context.
        assertTrue(Files.isDirectory(resources.resolve("src")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativeContextsSharePreparedResourcesAndEngineWithoutRewritingFiles() throws IOException {
        var configuration = nativeConfiguration(true);
        Path sentinel = resources.resolve("src/caller_owned.txt");
        Files.writeString(sentinel, "preserve");
        var hostAccess = GraalPyContextFactory.bootstrapHostAccess(getClass().getClassLoader());
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context primary = GraalPyContextFactory.buildContext(hostAccess, engine, getClass().getClassLoader(), configuration);
             Context pooled = GraalPyContextFactory.buildContext(hostAccess, engine, getClass().getClassLoader(), nativeConfigurationWithoutExtraction(true))) {
            exchange(primary);
            Files.delete(resources.resolve("unix.sock"));
            exchange(pooled);
            assertEquals("preserve", Files.readString(sentinel));
            var javaConfiguration = new GraalPyContextConfiguration();
            try (Context javaContext = GraalPyContextFactory.buildContext(
                hostAccess, engine, getClass().getClassLoader(), javaConfiguration)) {
                assertFalse(javaContext.eval("python", "import socket; hasattr(socket, 'AF_UNIX')").asBoolean());
            }
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void nativeContextPreservesConfiguredHostClassFilter() throws IOException {
        var configuration = nativeConfiguration(true);
        configuration.setHostClassLookup(List.of("example.allowed"));
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = buildContext(engine, configuration)) {
            assertTrue(context.eval("python", "import java; java.type('java.lang.String') is not None").asBoolean());
            assertThrows(PolyglotException.class, () -> context.eval("python", "java.type('org.junit.jupiter.api.Test')"));
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void loadsMainAndImportsFromPhysicalSourceDirectory() throws IOException {
        Path classpath = resources.resolve("classpath");
        Path resourceRoot = classpath.resolve(GraalPyContextFactory.APPLICATION_PATH);
        Files.createDirectories(resourceRoot.resolve("src"));
        Files.writeString(resourceRoot.resolve("src/native_main.py"), """
            from native_dependency import VALUE
            loaded_file = __file__
            answer = VALUE
            """);
        Files.writeString(resourceRoot.resolve("src/native_dependency.py"), "VALUE = 42\n");
        Files.writeString(resourceRoot.resolve("fileslist.txt"),
            "/" + GraalPyContextFactory.APPLICATION_SRC_PATH + "native_main.py\n/"
                + GraalPyContextFactory.APPLICATION_SRC_PATH + "native_dependency.py\n");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classpath.toUri().toURL()}, getClass().getClassLoader())) {
            extractResources(loader);
            try (Context context = GraalPyContextFactory.bootstrapReusableContext(loader,
                nativeConfigurationWithoutExtraction(true), "native_main.py")) {
                assertEquals(42, context.eval("python", "import sys; sys.modules['__main__'].answer").asInt());
                assertEquals(resources.resolve("src/native_main.py").toString(),
                    context.eval("python", "sys.modules['__main__'].loaded_file").asString());
                assertEquals(resources.resolve("src/native_dependency.py").toString(),
                    context.eval("python", "import native_dependency; native_dependency.__file__").asString());
            }
        }
    }

    private void exchange(Context context) {
        context.getBindings("python").putMember("socket_path", resources.resolve("unix.sock").toString());
        context.eval("python", UNIX_TRANSPORT);
    }

    private static IOAccess hostIo() {
        return IOAccess.newBuilder().allowHostFileAccess(true).allowHostSocketAccess(true).build();
    }

    private Context nativeExternalContext(boolean nativeAccess) throws IOException {
        extractResources(getClass().getClassLoader());
        return Context.newBuilder("python")
            .allowNativeAccess(nativeAccess)
            .allowIO(hostIo())
            .apply(GraalPyResources.forExternalDirectory(resources))
            .option("python.PosixModuleBackend", "native")
            .build();
    }

    private GraalPyContextConfiguration nativeConfiguration(boolean nativeAccess) throws IOException {
        extractResources(getClass().getClassLoader());
        return nativeConfigurationWithoutExtraction(nativeAccess);
    }

    private GraalPyContextConfiguration nativeConfigurationWithoutExtraction(boolean nativeAccess) {
        var configuration = new GraalPyContextConfiguration();
        configuration.setResourceDirectory(resources);
        configuration.setOptions(Map.of("python.PosixModuleBackend", "native"));
        configuration.getBuilder().allowNativeAccess(nativeAccess)
            .allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build());
        return configuration;
    }

    private Context buildContext(Engine engine, GraalPyContextConfiguration configuration) throws IOException {
        return GraalPyContextFactory.buildContext(GraalPyContextFactory.bootstrapHostAccess(getClass().getClassLoader()),
            engine, getClass().getClassLoader(), configuration);
    }

    private void extractResources(ClassLoader classLoader) throws IOException {
        System.setProperty("org.graalvm.python.vfs.allow_multiple", "true");
        System.setProperty("org.graalvm.python.vfs.multiple_vfs_checks_as_warning", "true");
        try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
            .resourceDirectory(GraalPyContextFactory.APPLICATION_PATH)
            .resourceClassLoader(classLoader)
            .build()) {
            GraalPyResources.extractVirtualFileSystemResources(vfs, resources);
        }
    }
}
