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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application that fails to start under the launcher, in a JVM of its own: the launcher stays running, an edit that
 * does not correct the failure is reported with its own cause, and the edit that corrects it makes the application
 * answer from the same process, whichever way the start failed.
 */
class LauncherReloadAfterFailedStartTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final Duration STILL_ALIVE = Duration.ofSeconds(2);
    private static final String FIRST_CAUSE = "first-cause-of-failure";
    private static final String SECOND_CAUSE = "second-cause-of-failure";

    @TempDir
    Path project;

    private Process process;
    private Path output;
    private int port;
    private Path sources;
    private Path resources;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        sources = Files.createDirectories(project.resolve("src/main/java/app"));
        resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.properties"), "micronaut.server.port=" + port + "\n");
        Files.writeString(sources.resolve("Application.java"), application(""));
        Files.writeString(sources.resolve("PidController.java"), """
            package app;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import io.micronaut.http.annotation.Produces;
            @Controller("/pid")
            public class PidController {
                @Get
                @Produces(MediaType.TEXT_PLAIN)
                public String pid() {
                    return String.valueOf(ProcessHandle.current().pid());
                }
            }
            """);
    }

    @AfterEach
    void stopLauncher() throws InterruptedException {
        if (process != null) {
            // not forcibly at first: a coverage agent writes what it measured as the JVM shuts down
            process.destroy();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @Test
    void aBeanThatFailsTheContextIsCorrectedByASourceEdit() throws Exception {
        Files.writeString(sources.resolve("Eager.java"), eagerBean(FIRST_CAUSE));
        launch();
        assertFailedWith(0, FIRST_CAUSE);

        int mark = logged().length();
        Files.writeString(sources.resolve("Eager.java"), eagerBean(SECOND_CAUSE));
        assertFailedWith(mark, SECOND_CAUSE);

        // nor does an edit that does not compile end the launcher, or the failed start
        mark = logged().length();
        Files.writeString(sources.resolve("Eager.java"), "package app; public class Eager { doesNotCompile }");
        assertLogged(mark, "compilation failed");

        Files.writeString(sources.resolve("Eager.java"), eagerBean(null));
        assertAnswersFromTheSameProcess();
    }

    @Test
    void aBeanTheServerNeedsIsCorrectedByASourceEdit() throws Exception {
        Files.writeString(sources.resolve("Processor.java"), errorProcessor(FIRST_CAUSE));
        launch();
        assertFailedWith(0, FIRST_CAUSE);

        int mark = logged().length();
        Files.writeString(sources.resolve("Processor.java"), errorProcessor(SECOND_CAUSE));
        assertFailedWith(mark, SECOND_CAUSE);

        Files.writeString(sources.resolve("Processor.java"), errorProcessor(null));
        assertAnswersFromTheSameProcess();
    }

    @Test
    void aMainThatFailsIsCorrectedByASourceEdit() throws Exception {
        Files.writeString(sources.resolve("Application.java"), application("if (true) { throw new IllegalStateException(\"" + FIRST_CAUSE + "\"); }"));
        launch();
        assertFailedWith(0, FIRST_CAUSE);

        int mark = logged().length();
        Files.writeString(sources.resolve("Application.java"), application("if (true) { throw new IllegalStateException(\"" + SECOND_CAUSE + "\"); }"));
        assertFailedWith(mark, SECOND_CAUSE);

        Files.writeString(sources.resolve("Application.java"), application(""));
        assertAnswersFromTheSameProcess();
    }

    @Test
    void aBadConfigurationIsCorrectedByAResourceEdit() throws Exception {
        Files.writeString(resources.resolve("application.properties"), "micronaut.server.port=first-bad-port\n");
        launch();
        assertFailedWith(0, "first-bad-port");

        int mark = logged().length();
        Files.writeString(resources.resolve("application.properties"), "micronaut.server.port=second-bad-port\n");
        assertFailedWith(mark, "second-bad-port");

        Files.writeString(resources.resolve("application.properties"), "micronaut.server.port=" + port + "\n");
        assertAnswersFromTheSameProcess();
    }

    @Test
    void aPortInUseIsCorrectedByTheNextChangeOnceItIsFree() throws Exception {
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port));
            launch();
            assertFailedWith(0, String.valueOf(port));

            int mark = logged().length();
            Files.writeString(sources.resolve("Application.java"), application("// still in use"));
            assertFailedWith(mark, String.valueOf(port));
        }
        Files.writeString(sources.resolve("Application.java"), application("// free now"));
        assertAnswersFromTheSameProcess();
    }

    private void assertFailedWith(int from, String cause) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && process.isAlive() && !failedWith(from, cause)) {
            Thread.sleep(50);
        }
        assertTrue(failedWith(from, cause), () -> "The failure was not reported, the launcher's status " + status() + ":\n" + logged());
        // what a failure prints may still come, then the launcher must still be there
        Thread.sleep(STILL_ALIVE.toMillis());
        assertTrue(process.isAlive(), () -> "The launcher exited with status " + status() + ":\n" + logged());
        assertFalse(logged().substring(from).contains("started with strategy"), this::logged);
    }

    private void assertLogged(int from, String text) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && process.isAlive() && !logged().substring(from).contains(text)) {
            Thread.sleep(50);
        }
        assertTrue(logged().substring(from).contains(text), () -> "Not logged: " + text + ", the launcher's status " + status() + ":\n" + logged());
        assertTrue(process.isAlive(), () -> "The launcher exited with status " + status() + ":\n" + logged());
    }

    private boolean failedWith(int from, String cause) {
        String text = logged();
        return from <= text.length() && text.substring(from).contains(cause) && text.substring(from).contains("failed to start");
    }

    private void assertAnswersFromTheSameProcess() throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        String answer = null;
        while (System.nanoTime() < deadline && process.isAlive() && answer == null) {
            answer = pid();
            if (answer == null) {
                Thread.sleep(100);
            }
        }
        String pid = answer;
        assertTrue(String.valueOf(process.pid()).equals(pid), () -> "Answered " + pid + ", the launcher's pid " + process.pid() + ", its status " + status() + ":\n" + logged());
    }

    private String pid() throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/pid")).timeout(Duration.ofSeconds(5)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void launch() throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // neither the test classes nor the test resources: only what an application has
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(entry -> !entry.replace('\\', '/').matches(".*/dev/build/(classes/[a-z]+|resources)/test/?"))
            .collect(Collectors.joining(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), List.of(classpath.split(File.pathSeparator)));
        Files.writeString(project.resolve("dev.properties"), """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.patch-in-place=false
            """);
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + classpath.replace("\\", "\\\\") + "\""));
        output = project.resolve("output.txt");
        List<String> command = new ArrayList<>(List.of(java));
        // a coverage agent measures the launcher too, the one place these paths run
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
            .forEach(command::add);
        command.addAll(List.of("@" + argfile, "-Xmx512m", MicronautDevMain.class.getName(), "--manifest", project.resolve("dev.properties").toString()));
        process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }

    private String status() {
        return process.isAlive() ? "running" : String.valueOf(process.exitValue());
    }

    private String logged() {
        try {
            return Files.readString(output);
        } catch (IOException e) {
            return e.toString();
        }
    }

    private static String application(String afterStart) {
        return """
            package app;
            public class Application {
                public static void main(String[] args) {
                    %s
                    io.micronaut.runtime.Micronaut.run(Application.class, args);
                }
            }
            """.formatted(afterStart);
    }

    private static String eagerBean(String failure) {
        return """
            package app;
            @io.micronaut.context.annotation.Context
            public class Eager {
                public Eager() {
                    %s
                }
            }
            """.formatted(failure == null ? "" : "if (true) { throw new IllegalStateException(\"" + failure + "\"); }");
    }

    private static String errorProcessor(String failure) {
        return """
            package app;
            import io.micronaut.http.MutableHttpResponse;
            import io.micronaut.http.server.exceptions.response.ErrorContext;
            import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
            @jakarta.inject.Singleton
            @io.micronaut.context.annotation.Primary
            public class Processor implements ErrorResponseProcessor<Object> {
                public Processor() {
                    %s
                }
                @Override
                public MutableHttpResponse<Object> processResponse(ErrorContext errorContext, MutableHttpResponse<?> baseResponse) {
                    return baseResponse.body(null);
                }
            }
            """.formatted(failure == null ? "" : "if (true) { throw new IllegalStateException(\"" + failure + "\"); }");
    }
}
