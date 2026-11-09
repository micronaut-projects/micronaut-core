package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the Netty HTTP client pays for its connections, against a Micronaut server on a loopback
 * socket: a request on a pooled connection, a request that opens its own connection, a refresh of
 * the client followed by a request (the refresh rebuilds the bootstrap and winds the pooled
 * connections down, so the request connects again), and requests while a background task keeps
 * refreshing the same client (only the requests are timed).
 *
 * <p>Every case has its own client, so a refresh in one case does not reach the connections of
 * another. Run with {@code -prof gc} for the allocations.
 *
 * <p>The cases that open connections run over HTTP/1 only: over h2c with prior knowledge, a
 * request on a new connection occasionally waits until the read timeout, and a refresh can close
 * requests that are still running, which fails the benchmark instead of measuring it.
 *
 * <p>Each new connection leaves a socket in {@code TIME_WAIT}, so the cases that connect are
 * bounded, or they run out of local ports: a request on a new connection and a refresh followed
 * by a request are timed in batches of {@value #CONNECTIONS_PER_BATCH} (the score is the time of a
 * batch), and the refreshes under traffic are {@value #REFRESH_PAUSE_MILLIS} ms apart.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ClientConnectionBenchmark {

    static final int CONNECTIONS_PER_BATCH = 100;
    static final long REFRESH_PAUSE_MILLIS = 20;
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
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(batchSize = CONNECTIONS_PER_BATCH)
    @Measurement(batchSize = CONNECTIONS_PER_BATCH)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public String unpooledRequest(Connecting state) {
        return state.unpooled.retrieve(PATH);
    }

    /**
     * A refresh, then a request, which connects again.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(batchSize = CONNECTIONS_PER_BATCH)
    @Measurement(batchSize = CONNECTIONS_PER_BATCH)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public String refreshThenRequest(Connecting state) {
        state.refreshing.refresh();
        return state.refreshing.toBlocking().retrieve(PATH);
    }

    /**
     * Requests on one client while a background task of the fixture refreshes it: only the
     * requests are timed.
     */
    @Benchmark
    @Threads(3)
    public String requestDuringRefreshes(UnderTraffic state) {
        return state.client.toBlocking().retrieve(PATH);
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

        @Setup
        public void setUp() {
            fixture.start(protocol);
            unpooled = fixture.client(false).toBlocking();
            refreshing = fixture.client(true);
        }

        @TearDown
        public void tearDown() {
            fixture.close();
        }
    }

    /**
     * A pooled client over HTTP/1 that a background task refreshes every
     * {@value #REFRESH_PAUSE_MILLIS} ms while an iteration runs.
     */
    @State(Scope.Benchmark)
    public static class UnderTraffic {
        @Param({"HTTP_1_1"})
        SocketServerBenchmark.Protocol protocol;

        final Fixture fixture = new Fixture();
        final AtomicLong refreshes = new AtomicLong();
        HttpClient client;
        ScheduledExecutorService refresher;
        ScheduledFuture<?> refreshing;

        @Setup(Level.Trial)
        public void setUp() {
            fixture.start(protocol);
            client = fixture.client(true);
        }

        @Setup(Level.Iteration)
        public void startRefreshing() {
            refreshes.set(0);
            refresher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("benchmark-refresher").factory());
            refreshing = refresher.scheduleWithFixedDelay(this::refresh, REFRESH_PAUSE_MILLIS, REFRESH_PAUSE_MILLIS, TimeUnit.MILLISECONDS);
        }

        @TearDown(Level.Iteration)
        public void stopRefreshing() throws InterruptedException {
            refresher.shutdownNow();
            if (!refresher.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The refresher did not stop");
            }
            // a periodic task is done only once a run failed: the refreshes stopped there, so the
            // iteration did not measure requests under refreshes
            if (refreshing.isDone() && !refreshing.isCancelled()) {
                try {
                    refreshing.get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("A refresh of the client failed", e.getCause());
                }
            }
            if (refreshes.get() == 0) {
                throw new IllegalStateException("The client was not refreshed during the iteration");
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            fixture.close();
        }

        private void refresh() {
            client.refresh();
            refreshes.incrementAndGet();
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
