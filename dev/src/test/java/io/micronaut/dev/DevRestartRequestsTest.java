package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requests that arrive while the development runtime restarts the application: the HTTP server's socket stays bound
 * across generations, a request in flight finishes on the generation it started on, and nothing fails with a 500.
 */
class DevRestartRequestsTest {

    @TempDir
    Path project;

    private int port;
    private Path src;
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
    void requestsDuringRestartsAreServedByEitherGeneration() throws Exception {
        DevRuntime runtime = launch();
        try {
            assertEquals("greeting-0", awaitServer());

            AtomicBoolean running = new AtomicBoolean(true);
            Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
            List<String> failures = new CopyOnWriteArrayList<>();
            List<Thread> hammers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Thread thread = new Thread(() -> {
                    while (running.get()) {
                        String outcome;
                        try {
                            outcome = get("/hello");
                        } catch (IOException e) {
                            outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
                        }
                        outcomes.computeIfAbsent(outcome.startsWith("200") ? "200" : outcome, k -> new AtomicInteger()).incrementAndGet();
                        if (!outcome.startsWith("200")) {
                            failures.add(outcome);
                        }
                    }
                }, "hammer-" + i);
                thread.start();
                hammers.add(thread);
            }

            // a slow request in flight when the restart begins completes on the generation it started on
            String[] slow = new String[1];
            Thread slowThread = new Thread(() -> {
                try {
                    slow[0] = get("/slow");
                } catch (IOException e) {
                    slow[0] = e.toString();
                }
            });
            slowThread.start();
            Thread.sleep(200);

            for (int generation = 2; generation <= 4; generation++) {
                // a structural edit: a new method, so the class shape changes and the generation restarts
                Files.writeString(src.resolve("Greeter.java"), greeter(generation - 1));
                runtime.reload();
                ApplicationContext context = runtime.awaitGeneration(generation, Duration.ofMinutes(2));
                assertTrue(context.isRunning());
                Thread.sleep(300);
            }
            slowThread.join(30_000);
            running.set(false);
            for (Thread thread : hammers) {
                thread.join(70_000);
            }
            assertEquals(List.of(), failures, "Outcomes: " + outcomes);
            assertTrue(outcomes.get("200").get() > 0);
            assertEquals("200 slow-greeting-0", slow[0]);
            assertEquals("200 greeting-3", get("/hello"));
            assertEquals(List.of(), errorsLogged());
        } finally {
            runtime.close();
        }
    }

    @Test
    void aGenerationThatFailsToStartAnswers503UntilTheNextOneServes() throws Exception {
        DevRuntime runtime = launch();
        try {
            assertEquals("greeting-0", awaitServer());

            // a bean that fails the context's startup: the generation does not start, and the port answers at once
            Path boom = src.resolve("Boom.java");
            Files.writeString(boom, """
                package app;
                @io.micronaut.context.annotation.Context
                public class Boom { Boom() { throw new IllegalStateException("boom"); } }
                """);
            runtime.reload();
            assertTrue(runtime.isStartFailed());
            String unavailable = get("/hello");
            assertTrue(unavailable.startsWith("503 "), unavailable);
            assertTrue(unavailable.contains("The application is not running"), unavailable);
            assertTrue(rawGet("/hello").contains("\r\nRetry-After: 1\r\n"));

            // the fix: the next generation accepts on the same socket
            Files.delete(boom);
            Files.writeString(src.resolve("Greeter.java"), greeter(1));
            runtime.reload();
            assertEquals("200 greeting-1", get("/hello"));
        } finally {
            runtime.close();
        }
        // the runtime closed the socket it kept
        try (Socket ignored = new Socket("localhost", port)) {
            throw new AssertionError("The port is still bound");
        } catch (IOException expected) {
            // refused
        }
    }

    @Test
    void theFirstGenerationIsReportedStartedOnceItsServerAcceptsAndAnEditStraightAwayRestartsIt() throws Exception {
        DevRuntime runtime = launch();
        try {
            // as after a restart, the first start returns once the server holding the retained socket accepts
            ApplicationContext first = runtime.awaitGeneration(1, Duration.ofSeconds(1));
            List<EmbeddedServer> servers = first.getActiveBeanRegistrations(EmbeddedServer.class).stream()
                .map(io.micronaut.context.BeanRegistration::getBean)
                .toList();
            assertEquals(1, servers.size());
            assertTrue(servers.get(0).isRunning());

            // an edit taken at once must not close the socket under the server: it used to, and the startup
            // failure then exited the development JVM
            Files.writeString(src.resolve("Greeter.java"), greeter(1));
            runtime.reload();
            assertTrue(runtime.awaitGeneration(2, Duration.ofMinutes(2)).isRunning());
            assertEquals("200 greeting-1", get("/hello"));
            assertEquals(List.of(), errorsLogged());
        } finally {
            runtime.close();
        }
    }

    @Test
    void aFirstGenerationWhoseServerIsCreatedLateIsNotReportedStartedBeforeItAccepts() throws Exception {
        // the server is created after the context started, and this one takes a while: no socket is bound in the meantime
        DevRuntime runtime = launch(Map.of("SlowServer.java", """
            package app;
            @jakarta.inject.Singleton
            public class SlowServer implements io.micronaut.context.event.BeanCreatedEventListener<io.micronaut.runtime.server.EmbeddedServer> {
                @Override
                public io.micronaut.runtime.server.EmbeddedServer onCreated(io.micronaut.context.event.BeanCreatedEvent<io.micronaut.runtime.server.EmbeddedServer> event) {
                    try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return event.getBean();
                }
            }
            """));
        try {
            ApplicationContext first = runtime.awaitGeneration(1, Duration.ofSeconds(1));
            List<EmbeddedServer> servers = first.getActiveBeanRegistrations(EmbeddedServer.class).stream()
                .map(io.micronaut.context.BeanRegistration::getBean)
                .toList();
            assertEquals(1, servers.size());
            assertTrue(servers.get(0).isRunning());
            assertEquals("200 greeting-0", get("/hello"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aFirstGenerationWhoseServerCannotBindIsReportedFailedNotStarted() throws Exception {
        DevRuntime runtime;
        // the port is taken before the application starts: its server cannot bind, and its context stops again.
        // Netty binds the IPv6 wildcard, so the port is held dual stack
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(port));
            runtime = launch();
        }
        try {
            assertTrue(runtime.isStartFailed());
            // a caller waiting for the generation sees the failure, not its stopped context
            assertThrows(IllegalStateException.class, () -> runtime.awaitGeneration(1, Duration.ofSeconds(1)));
            List<String> messages = logged.list.stream()
                .filter(event -> event.getLoggerName().equals(DevRuntime.class.getName()))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertTrue(messages.stream().noneMatch(message -> message.contains("generation 1 started")), messages.toString());
            List<ILoggingEvent> failures = logged.list.stream()
                .filter(event -> event.getLoggerName().equals(DevRuntime.class.getName()))
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .filter(event -> event.getFormattedMessage().contains("generation 1 failed to start"))
                .toList();
            assertEquals(1, failures.size(), messages.toString());
            assertTrue(failures.get(0).getFormattedMessage().contains(String.valueOf(port)), failures.get(0).getFormattedMessage());

            // once the port is free, the next change starts a generation that serves
            Files.writeString(src.resolve("Greeter.java"), greeter(1));
            runtime.reload();
            assertTrue(runtime.awaitGeneration(2, Duration.ofMinutes(2)).isRunning());
            assertEquals("200 greeting-1", get("/hello"));
            assertFalse(runtime.isStartFailed());
        } finally {
            runtime.close();
        }
    }

    @Test
    void anApplicationThatDefinesAServerButNeverStartsOneIsNotHeldWaitingForIt() throws Exception {
        // a command line application with the server on its classpath: its main runs a context and returns
        long start = System.nanoTime();
        DevRuntime runtime = launch(Map.of("Application.java", """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.context.ApplicationContext.builder(Application.class).properties(java.util.Map.of("spec.name", "DevRestartRequestsTest")).start();
                }
            }
            """));
        try {
            ApplicationContext first = runtime.awaitGeneration(1, Duration.ofSeconds(1));
            assertTrue(first.getActiveBeanRegistrations(EmbeddedServer.class).isEmpty());
            // well within the 10 s a server is waited for
            assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(8)) < 0, "held waiting for a server never started");
        } finally {
            runtime.close();
        }
    }

    private DevRuntime launch() throws IOException {
        return launch(Map.of());
    }

    private DevRuntime launch(Map<String, String> sources) throws IOException {
        src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRestartRequestsTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("HelloController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller("/")
            public class HelloController {
                private final Greeter greeter;
                HelloController(Greeter greeter) { this.greeter = greeter; }
                @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
                public String hello() { return greeter.greet(); }
                @io.micronaut.http.annotation.Get(value = "/slow", produces = "text/plain")
                @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
                public String slow() throws InterruptedException { Thread.sleep(1500); return "slow-" + greeter.greet(); }
            }
            """);
        Files.writeString(src.resolve("Greeter.java"), greeter(0));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Files.writeString(src.resolve(source.getKey()), source.getValue());
        }
        Path manifestFile = project.resolve("dev.properties");
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
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
        return new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
    }

    private List<String> errorsLogged() {
        return logged.list.stream()
            .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR) || event.getThrowableProxy() != null && event.getThrowableProxy().getClassName().contains("NullPointerException"))
            .map(event -> event.getLoggerName() + ": " + event.getFormattedMessage())
            .toList();
    }

    /**
     * One request on its own connection, as curl sends it: no client retries a refused or reset connection.
     *
     * @return The status and the body
     */
    private String get(String path) throws IOException {
        String response = rawGet(path);
        if (response.isEmpty()) {
            return "empty reply";
        }
        String status = response.substring("HTTP/1.1 ".length(), response.indexOf('\r'));
        return status.substring(0, 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    private String rawGet(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The first generation's server binds after its context runs, which is when the launch returns.
     */
    private String awaitServer() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                String response = get("/hello");
                assertTrue(response.startsWith("200 "), response);
                return response.substring(4);
            } catch (IOException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
    }

    private static String greeter(int version) {
        StringBuilder members = new StringBuilder();
        for (int i = 0; i < version; i++) {
            members.append(" public int extra").append(i).append("() { return ").append(i).append("; }");
        }
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"greeting-" + version + "\"; }" + members + " }";
    }
}
