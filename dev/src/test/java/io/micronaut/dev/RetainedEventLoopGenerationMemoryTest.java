package io.micronaut.dev;

import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first generation of an application whose HTTP server and declarative clients ran on the event loops retained
 * across restarts is collected: what the first generation's channels left on the retained event loop threads, their
 * context class loader, their thread locals and their recyclers, does not keep it, nor does the client connection the
 * next generations took back, which the remote it is connected to accepted once.
 * <p>The application runs in a JVM of its own: Netty must not have been initialized there by any other test.</p>
 */
class RetainedEventLoopGenerationMemoryTest {

    private static final String PROBE = "probe: ";
    private static final String COLLECTED = PROBE + "generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theFirstGenerationIsCollectedWhileItsEventLoopsAreRetained() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + System.getProperty("java.class.path").replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Xmx512m"));
        String dump = System.getProperty("probe.dump");
        if (dump != null) {
            // a heap dump of the probe when generation 1 stays reachable, to find what keeps it
            command.add("-Dprobe.dump=" + dump);
        }
        command.add(Probe.class.getName());
        command.add(project.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                output.append(e);
            }
        });
        reader.start();
        boolean exited = process.waitFor(4, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        String tail = output.length() > 6000 ? output.substring(output.length() - 6000) : output.toString();
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(output.toString().contains(COLLECTED), tail);
        assertEquals(0, process.exitValue(), tail);
    }

    /**
     * Runs an application with a controller and a declarative client in development mode, sends requests through
     * both in each generation, restarts it twice and reports whether its first generation was collected while the
     * event loop group stayed the same.
     */
    public static final class Probe {

        public static void main(String[] args) throws Exception {
            Path project = Path.of(args[0]);
            int status;
            try {
                status = run(project);
            } catch (Throwable e) {
                e.printStackTrace(System.out);
                status = 2;
            }
            System.out.flush();
            System.exit(status);
        }

        private static int run(Path project) throws Exception {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            // a remote whose connections the client keeps across the restarts
            Set<InetSocketAddress> accepted = ConcurrentHashMap.newKeySet();
            HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.createContext("/echo", exchange -> {
                accepted.add(exchange.getRemoteAddress());
                byte[] body = "upstream".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            upstream.start();
            try {
                return run(project, port, upstream, accepted);
            } finally {
                upstream.stop(0);
            }
        }

        private static int run(Path project, int port, HttpServer upstream, Set<InetSocketAddress> accepted) throws Exception {
            Path src = Files.createDirectories(project.resolve("src/main/java/app"));
            Files.writeString(src.resolve("Application.java"), """
                package app;
                public class Application {
                    public static void main(String[] args) {
                        io.micronaut.runtime.Micronaut.build(args)
                            .properties(java.util.Map.of("spec.name", "RetainedEventLoopGenerationMemoryTest", "micronaut.server.port", args[0], "upstream.url", args[1]))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.writeString(src.resolve("HelloClient.java"), """
                package app;
                @io.micronaut.http.client.annotation.Client("http://localhost:${micronaut.server.port}")
                public interface HelloClient {
                    @io.micronaut.http.annotation.Get(value = "/hello", consumes = "text/plain")
                    String hello();
                }
                """);
            Files.writeString(src.resolve("UpstreamClient.java"), """
                package app;
                @io.micronaut.http.client.annotation.Client("${upstream.url}")
                public interface UpstreamClient {
                    @io.micronaut.http.annotation.Get(value = "/echo", consumes = "text/plain")
                    String echo();
                }
                """);
            Files.writeString(src.resolve("HelloController.java"), controller("hello-1"));
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
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port), "http://127.0.0.1:" + upstream.getAddress().getPort()});
            try {
                expect(port, "hello-1");
                WeakReference<ClassLoader> first = firstLoader(runtime);
                WeakReference<EventLoopGroup> group = defaultGroup(runtime);
                for (int generation = 2; generation <= 3; generation++) {
                    Files.writeString(src.resolve("HelloController.java"), controller("hello-" + generation));
                    runtime.reload();
                    runtime.awaitGeneration(generation, Duration.ofMinutes(2));
                    expect(port, "hello-" + generation);
                }
                if (group.get() != defaultGroup(runtime).get()) {
                    System.out.println(PROBE + "the event loop group was not retained");
                    return 1;
                }
                if (accepted.size() != 1) {
                    System.out.println(PROBE + "the client connection was not kept: the remote accepted " + accepted);
                    return 1;
                }
                long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (first.get() != null && System.nanoTime() < deadline) {
                    System.gc();
                    Thread.sleep(200);
                }
                if (first.get() == null) {
                    System.out.println(COLLECTED);
                    return 0;
                }
                String dump = System.getProperty("probe.dump");
                if (dump != null) {
                    java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).dumpHeap(dump, true);
                }
                System.out.println(PROBE + "generation 1 is still reachable");
                return 1;
            } finally {
                runtime.close();
            }
        }

        private static String controller(String hello) {
            return """
                package app;
                @io.micronaut.http.annotation.Controller
                public class HelloController {
                    private final HelloClient client;
                    private final UpstreamClient upstream;
                    HelloController(HelloClient client, UpstreamClient upstream) {
                        this.client = client;
                        this.upstream = upstream;
                    }
                    @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
                    public String hello() { return "%s"; }
                    @io.micronaut.http.annotation.Get(value = "/client", produces = "text/plain")
                    @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
                    public String client() { return client.hello(); }
                    @io.micronaut.http.annotation.Get(value = "/upstream", produces = "text/plain")
                    @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
                    public String upstream() { return upstream.echo(); }
                }
                """.formatted(hello);
        }

        /**
         * Sends a request to the server, and one through the client, which the server answers.
         */
        private static void expect(int port, String hello) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            String direct = null;
            while (direct == null) {
                try {
                    direct = get(port, "/hello");
                } catch (IOException e) {
                    if (System.nanoTime() > deadline) {
                        throw e;
                    }
                    Thread.sleep(50);
                }
            }
            String viaClient = get(port, "/client");
            if (!direct.equals(hello) || !viaClient.equals(hello)) {
                throw new IllegalStateException("expected " + hello + ", got " + direct + " and " + viaClient + " through the client");
            }
            String viaUpstream = get(port, "/upstream");
            if (!viaUpstream.equals("upstream")) {
                throw new IllegalStateException("expected the upstream's answer, got " + viaUpstream);
            }
        }

        private static String get(int port, String path) throws IOException {
            try (Socket socket = new Socket("localhost", port)) {
                socket.setSoTimeout(60_000);
                socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return response.substring(response.indexOf("\r\n\r\n") + 4);
            }
        }

        /**
         * A weak reference to the first generation's loader, taken in a frame of its own so that no local keeps it.
         */
        private static WeakReference<ClassLoader> firstLoader(DevRuntime runtime) {
            ApplicationContext context = runtime.context().orElseThrow();
            return new WeakReference<>(context.getClassLoader());
        }

        private static WeakReference<EventLoopGroup> defaultGroup(DevRuntime runtime) {
            return new WeakReference<>(runtime.context().orElseThrow().getBean(EventLoopGroup.class));
        }
    }
}
