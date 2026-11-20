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
package io.micronaut.dev;

import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compiler or an annotation processor that throws during a reload, rather than reporting an error, fails the reload as a
 * compilation error does: the running generation answers with the failure page, the edit comes again with the next
 * change, and the edit that compiles clears the page. What throws once a new generation started does not.
 */
class CompilerExceptionPageTest {

    @TempDir
    Path project;

    private int port;
    private Path src;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        src = Files.createDirectories(project.resolve("src/main/java/app"));
    }

    @Test
    void aProcessorThatThrowsShowsTheFailurePageUntilAnEditCompiles() throws Exception {
        Path hello = src.resolve("HelloController.java");
        Path other = src.resolve("Other.java");
        Files.writeString(other, "package app; public class Other { public static String value() { return \"one\"; } }");
        DevRuntime runtime = launch();
        try {
            String token = DevAccess.TOKEN_HEADER + ": " + runtime.access().token() + "\r\n";
            assertEquals("200 hello", status(get("/hello", "")));

            // the processor throws: the page shows what it threw, and the generation that runs keeps running
            Files.writeString(hello, hello("@io.micronaut.dev.ThrowingProcessor.Fail", "broken"));
            runtime.reload();
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertTrue(runtime.lastFailure().get().describe().contains("ThrowingProcessor failed on [app.HelloController]"), runtime.lastFailure().get().describe());
            String failed = get("/hello", "Accept: application/json\r\n" + token);
            assertTrue(failed.startsWith("HTTP/1.1 503 "), failed);
            assertTrue(failed.contains("ThrowingProcessor failed on [app.HelloController]"), failed);
            assertEquals(1, runtime.generation());

            // an edit of another file compiles the one that threw with it, which throws again
            Files.writeString(other, "package app; public class Other { public static String value() { return \"two\"; } }");
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertTrue(get("/hello", "").startsWith("HTTP/1.1 503 "));
            assertEquals(1, runtime.generation());

            // the fix clears the page and serves both edits
            Files.writeString(hello, hello("", "fixed"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("200 fixed", status(get("/hello", "")));
            assertEquals("two", runtime.context().orElseThrow().getClassLoader().loadClass("app.Other").getMethod("value").invoke(null));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aListenerOfTheReloadThatThrowsLeavesTheNewGenerationServing() throws Exception {
        Path hello = src.resolve("HelloController.java");
        Files.writeString(src.resolve("FailingListener.java"), """
            package app;
            @jakarta.inject.Singleton
            public class FailingListener implements io.micronaut.context.event.ApplicationEventListener<io.micronaut.context.reload.ReloadCompletedEvent> {
                @Override
                public void onApplicationEvent(io.micronaut.context.reload.ReloadCompletedEvent event) {
                    throw new IllegalStateException("FailingListener failed");
                }
            }
            """);
        DevRuntime runtime = launch();
        try {
            // the restart started the new generation before the listener threw: it serves the edit, with no failure page
            Files.writeString(hello, hello("", "restarted"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("200 restarted", status(get("/hello", "")));
        } finally {
            runtime.close();
        }
    }

    private DevRuntime launch() throws IOException {
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "CompilerExceptionPageTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("HelloController.java"), hello("", "hello"));
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
        // the test's processor, named by a service file only this project's processor path has
        Path processor = project.resolve("processor");
        Files.createDirectories(processor.resolve("META-INF/services"));
        Files.writeString(processor.resolve("META-INF/services/javax.annotation.processing.Processor"), ThrowingProcessor.class.getName());
        List<String> processorPath = new ArrayList<>(classpath);
        processorPath.add(processor.toString());
        Files.write(project.resolve("processors.argfile"), processorPath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@processors.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);
        return new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
    }

    private static String hello(String annotation, String greeting) {
        return """
            package app;
            %s
            @io.micronaut.http.annotation.Controller("/hello")
            public class HelloController {
                @io.micronaut.http.annotation.Get(produces = "text/plain")
                public String hello() { return "%s"; }
            }
            """.formatted(annotation, greeting);
    }

    private static String status(String response) {
        return response.substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    private String get(String path, String headers) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\n" + headers + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
