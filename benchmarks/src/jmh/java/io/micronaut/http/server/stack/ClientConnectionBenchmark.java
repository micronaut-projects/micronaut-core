package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * What the Netty HTTP client pays for its connections, against a Micronaut server on a loopback
 * socket: a request on a pooled connection, a request that opens its own connection, a refresh of
 * the client followed by a request (the refresh rebuilds the bootstrap and winds the pooled
 * connections down, so the request connects again), and requests while another thread keeps
 * refreshing the same client.
 *
 * <p>Every case has its own client, so a refresh in one case does not reach the connections of
 * another. Run with {@code -prof gc} for the allocations.
 *
 * <p>The cases that open connections run over HTTP/1 only: over h2c with prior knowledge, a
 * request on a new connection occasionally waits until the read timeout, and a refresh can close
 * requests that are still running, which fails the benchmark instead of measuring it.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ClientConnectionBenchmark {

    private static final String PATH = "/socket-bench/loop";

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(ClientConnectionBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .forks(1)
            .build();
        new Runner(opt).run();
    }

    /**
     * A request on a pooled connection.
     */
    @Benchmark
    public String pooledRequest(Pooled state) {
        return state.client.retrieve(PATH);
    }

    /**
     * A request with the pool disabled: each request connects.
     */
    @Benchmark
    public String unpooledRequest(Connecting state) {
        return state.unpooled.retrieve(PATH);
    }

    /**
     * A refresh, then a request, which connects again.
     */
    @Benchmark
    public String refreshThenRequest(Connecting state) {
        state.refreshing.refresh();
        return state.refreshing.toBlocking().retrieve(PATH);
    }

    /**
     * Requests on one client while another thread refreshes it.
     */
    @Benchmark
    @Group("refreshUnderTraffic")
    @GroupThreads(3)
    public String requestDuringRefreshes(Connecting state) {
        return state.underTraffic.toBlocking().retrieve(PATH);
    }

    /**
     * The refreshes that the requests of the group run into.
     */
    @Benchmark
    @Group("refreshUnderTraffic")
    @GroupThreads(1)
    public void refreshDuringRequests(Connecting state) {
        state.underTraffic.refresh();
        Blackhole.consumeCPU(200_000);
    }

    /**
     * A pooled client, over both protocols.
     */
    @State(Scope.Benchmark)
    public static class Pooled {
        @Param({"HTTP_1_1", "H2C"})
        SocketServerBenchmark.Protocol protocol;

        final Fixture fixture = new Fixture();
        BlockingHttpClient client;

        @Setup
        public void setUp() {
            fixture.start(protocol);
            client = fixture.client(true).toBlocking();
        }

        @TearDown
        public void tearDown() {
            fixture.close();
        }
    }

    /**
     * The clients of the cases that open connections, over HTTP/1.
     */
    @State(Scope.Benchmark)
    public static class Connecting {
        @Param({"HTTP_1_1"})
        SocketServerBenchmark.Protocol protocol;

        final Fixture fixture = new Fixture();
        BlockingHttpClient unpooled;
        HttpClient refreshing;
        HttpClient underTraffic;

        @Setup
        public void setUp() {
            fixture.start(protocol);
            unpooled = fixture.client(false).toBlocking();
            refreshing = fixture.client(true);
            underTraffic = fixture.client(true);
        }

        @TearDown
        public void tearDown() {
            fixture.close();
        }
    }

    /**
     * A server on a loopback socket, and clients in contexts of their own.
     */
    static final class Fixture {
        private ApplicationContext serverContext;
        private final List<ApplicationContext> clientContexts = new ArrayList<>();
        private SocketServerBenchmark.Protocol protocol;
        private URL url;

        void start(SocketServerBenchmark.Protocol protocol) {
            this.protocol = protocol;
            serverContext = ApplicationContext.run(Map.of(
                "spec.name", SocketServerBenchmark.SPEC_NAME,
                "micronaut.server.port", -1,
                "micronaut.server.http-version", protocol == SocketServerBenchmark.Protocol.H2C ? "2.0" : "1.1"
            ));
            url = serverContext.getBean(EmbeddedServer.class).start().getURL();
        }

        /**
         * A client of its own context, after its first request, which loads its codecs and
         * connects.
         */
        HttpClient client(boolean pool) {
            ApplicationContext context = ApplicationContext.run(Map.of(
                "spec.name", "ClientConnectionBenchmark",
                "micronaut.http.client.plaintext-mode", protocol == SocketServerBenchmark.Protocol.H2C ? "h2c_prior_knowledge" : "http_1",
                "micronaut.http.client.pool.enabled", pool
            ));
            clientContexts.add(context);
            HttpClient client = context.createBean(HttpClient.class, url);
            client.toBlocking().retrieve(PATH);
            return client;
        }

        void close() {
            for (ApplicationContext context : clientContexts) {
                context.close();
            }
            clientContexts.clear();
            serverContext.close();
        }
    }
}
