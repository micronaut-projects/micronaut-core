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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The development pages of a running application, here a stand-in for the Control Panel at a configured path, answer
 * only allowed hosts and the launch's access token, which the startup note links to; the application's own routes are
 * left alone, and a failed compilation shows its source paths only with the token.
 */
class DevAccessTest {

    @TempDir
    Path project;

    private int port;
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        logged.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logged);
    }

    @Test
    void theDevelopmentPagesNeedAnAllowedHostAndTheTokenOfTheLaunch() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevAccessTest", "micronaut.server.port", args[0], "micronaut.control-panel.path", "/admin"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("PanelController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller("${micronaut.control-panel.path:/control-panel}")
            public class PanelController {
                @io.micronaut.http.annotation.Get(produces = "text/plain")
                public String index() { return "panel"; }
                @io.micronaut.http.annotation.Get(value = "/api/beans", produces = "text/plain")
                public String beans() { return "beans"; }
            }
            """);
        Path hello = src.resolve("HelloController.java");
        Files.writeString(hello, hello("hello"));
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
        try {
            String token = runtime.access().token();
            // 256 bits, URL safe
            assertEquals(43, token.length(), token);
            assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
            String local = "localhost:" + port;

            // the note links to the configured Control Panel path with the token
            String notice = logged.list.stream().map(ILoggingEvent::getFormattedMessage).filter(message -> message.contains(DevModeNotice.HEADLINE))
                .findFirst().orElseThrow();
            // on exactly one line of its own, the only place the token is shown
            assertEquals(List.of("Development pages: http://localhost:" + port + "/admin?token=" + token),
                notice.lines().filter(line -> line.contains(token)).toList(), notice);

            // the application's own routes are left alone
            assertEquals("200 hello", status(get("/hello", "rebind.attacker.example:" + port, "")));

            // a page of a site whose name was made to resolve to this machine, even with the token
            assertTrue(get("/admin", "rebind.attacker.example:" + port, header(token)).startsWith("HTTP/1.1 403 "));
            // no token
            String refused = get("/admin", local, "");
            assertTrue(refused.startsWith("HTTP/1.1 401 "), refused);
            assertTrue(refused.contains("Open the link the launcher logged at startup"), refused);
            assertTrue(get("/admin/api/beans", local, "").startsWith("HTTP/1.1 401 "));

            // the link sets the cookie and redirects to the page without the token
            String redirect = get("/admin?token=" + token, local, "");
            assertTrue(redirect.startsWith("HTTP/1.1 303 "), redirect);
            assertTrue(redirect.contains("\r\nlocation: /admin\r\n") || redirect.contains("\r\nLocation: /admin\r\n"), redirect);
            String setCookie = redirect.lines().filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("set-cookie:")).findFirst().orElseThrow();
            assertTrue(setCookie.contains(runtime.access().cookieName() + "=" + token), setCookie);
            assertTrue(setCookie.toLowerCase(java.util.Locale.ROOT).contains("httponly"), setCookie);
            assertTrue(setCookie.contains("SameSite=Strict"), setCookie);

            // the cookie, or the header, opens the pages
            assertEquals("200 panel", status(get("/admin", local, "Cookie: " + runtime.access().cookieName() + "=" + token + "\r\n")));
            assertEquals("200 beans", status(get("/admin/api/beans", "127.0.0.1:" + port, header(token))));

            // a failed compilation: its source paths only with the token
            Files.writeString(hello, hello("hello").replace("return", "retur"));
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            String generic = get("/hello", local, "Accept: text/html\r\n");
            assertTrue(generic.startsWith("HTTP/1.1 503 "), generic);
            assertTrue(generic.contains("see the console"), generic);
            assertFalse(generic.contains("HelloController.java"), generic);
            String detailed = get("/hello", local, "Accept: application/json\r\n" + header(token));
            assertTrue(detailed.startsWith("HTTP/1.1 503 "), detailed);
            assertTrue(detailed.contains("HelloController.java"), detailed);
            assertTrue(get("/hello", "rebind.attacker.example:" + port, "").startsWith("HTTP/1.1 403 "));

            // the token survives a reload
            Files.writeString(hello, hello("again"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals(token, runtime.access().token());
            assertEquals("200 again", status(get("/hello", local, "")));
            assertEquals("200 panel", status(get("/admin", local, header(token))));
        } finally {
            runtime.close();
        }
    }

    private static String header(String token) {
        return DevAccess.TOKEN_HEADER + ": " + token + "\r\n";
    }

    private static String hello(String greeting) {
        return """
            package app;
            @io.micronaut.http.annotation.Controller("/hello")
            public class HelloController {
                @io.micronaut.http.annotation.Get(produces = "text/plain")
                public String hello() { return "%s"; }
            }
            """.formatted(greeting);
    }

    private static String status(String response) {
        return response.substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    private String get(String path, String host, String headers) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\n" + headers + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
