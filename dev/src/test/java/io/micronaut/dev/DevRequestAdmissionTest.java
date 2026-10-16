package io.micronaut.dev;

import io.micronaut.context.reload.RequestAdmission;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.manifest.DevManifest;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link RequestAdmission} the runtime provides to server runtimes that hold requests at their own level, below
 * the gate filter: it carries the manifest's timeouts, and holds a request while a batch compiles, before the running
 * generation stops.
 */
class DevRequestAdmissionTest {

    @TempDir
    Path project;

    private int port;
    private Path src;
    private final GatedCompiler gated = new GatedCompiler();

    @Test
    void theAdmissionCarriesTheManifestTimeoutsWhileTheRuntimeRuns() throws Exception {
        DevRuntime runtime = launch();
        RequestAdmission admission;
        try {
            admission = RequestAdmission.current();
            assertNotNull(admission);
            assertSame(runtime.requestAdmission(), admission);
            // only the manifest sets them: the generation's environment has neither
            assertEquals(Duration.ofSeconds(45), admission.holdTimeout());
            assertEquals(Duration.ofMillis(4500), admission.drainTimeout());
            assertTrue(runtime.awaitGeneration(1, Duration.ofSeconds(1)).getProperty(DevManifest.REQUESTS_HOLD_TIMEOUT, String.class).isEmpty());
            assertTrue(admission.isAdmitted());
        } finally {
            runtime.close();
        }
        assertNull(RequestAdmission.current());
    }

    @Test
    void aRequestHeldThroughTheAdmissionWaitsForTheCompilationAndReachesTheNextGeneration() throws Exception {
        DevRuntime runtime = launch();
        Thread reload = null;
        try {
            assertEquals("200 greeting-0", awaitServer());
            RequestAdmission admission = RequestAdmission.current();
            assertNotNull(admission);

            // the next compilation blocks until released: the batch is in its compile phase, the generation still runs
            CountDownLatch release = gated.arm();
            Files.writeString(src.resolve("Greeter.java"), greeter(1));
            reload = new Thread(runtime::reload, "reload");
            reload.start();
            assertTrue(gated.entered.await(60, TimeUnit.SECONDS), "the compilation did not start");

            assertFalse(admission.isAdmitted());
            assertFalse(admission.awaitAdmission(Duration.ofMillis(100)));

            // a server holding at its own level: an asynchronous one on the stage, a blocking one on a thread
            AtomicBoolean released = new AtomicBoolean();
            CompletableFuture<Boolean> admittedAfterCompilation = admission.whenAdmitted().thenApply(ignored -> released.get()).toCompletableFuture();
            CompletableFuture<String> held = CompletableFuture.supplyAsync(() -> {
                try {
                    // as a server that holds at its own level: once admitted, the request goes on to the server
                    return admission.awaitAdmission(admission.holdTimeout()) ? get("/hello") : "not admitted";
                } catch (InterruptedException | IOException e) {
                    return e.toString();
                }
            });
            Thread.sleep(300);
            assertFalse(held.isDone(), "admitted during the compilation");
            assertFalse(admittedAfterCompilation.isDone());

            released.set(true);
            release.countDown();
            assertTrue(admittedAfterCompilation.get(60, TimeUnit.SECONDS));
            // admitted as the restart drains the old generation, which no longer accepts: the next one answers
            assertEquals("200 greeting-1", held.get(60, TimeUnit.SECONDS));
            reload.join(60_000);
            assertTrue(admission.isAdmitted());
        } finally {
            gated.disarm();
            if (reload != null) {
                reload.join(60_000);
            }
            runtime.close();
        }
    }

    private DevRuntime launch() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRequestAdmissionTest", "micronaut.server.port", args[0]))
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
            }
            """);
        Files.writeString(src.resolve("Greeter.java"), greeter(0));
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
            micronaut.dev.requests.hold-timeout=45s
            micronaut.dev.requests.drain-timeout=4500ms
            """);
        MicronautDevMain main = new MicronautDevMain() {
            @Override
            protected Map<SourceKind, SourceCompiler> createCompilers(DevManifest manifest) {
                Map<SourceKind, SourceCompiler> compilers = new EnumMap<>(DevRuntime.availableCompilers());
                gated.delegate = compilers.get(SourceKind.JAVA);
                compilers.put(SourceKind.JAVA, gated);
                return compilers;
            }
        };
        return main.launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
    }

    private String awaitServer() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                return get("/hello");
            } catch (IOException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
    }

    private String get(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (response.isEmpty()) {
                return "empty reply";
            }
            return response.substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
        }
    }

    private static String greeter(int version) {
        StringBuilder members = new StringBuilder();
        for (int i = 0; i < version; i++) {
            members.append(" public int extra").append(i).append("() { return ").append(i).append("; }");
        }
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"greeting-" + version + "\"; }" + members + " }";
    }

    /**
     * The Java compiler, whose next compilation once armed waits until released.
     */
    private static final class GatedCompiler implements SourceCompiler {
        volatile SourceCompiler delegate;
        volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(0);
        private final AtomicBoolean armed = new AtomicBoolean();

        CountDownLatch arm() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            armed.set(true);
            return release;
        }

        void disarm() {
            armed.set(false);
            release.countDown();
        }

        @Override
        public Set<SourceKind> kinds() {
            return delegate.kinds();
        }

        @Override
        public Set<SourceKind> jointKinds() {
            return delegate.jointKinds();
        }

        @Override
        public CompilationResult compile(CompilationRequest request) {
            if (armed.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.compile(request);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
