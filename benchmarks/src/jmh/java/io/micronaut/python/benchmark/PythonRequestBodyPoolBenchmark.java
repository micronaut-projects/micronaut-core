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
package io.micronaut.python.benchmark;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.python.PythonContextExecutor;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end reproduction for pooled Python controller request-body conversion contention.
 *
 * <p>Each invocation sends a real HTTP request through Netty JSON decoding, body binding,
 * generated Python wrapper conversion, and pooled controller execution. The control endpoint
 * isolates the general cost of the same pooled controller without a data-class body.</p>
 *
 * <p>Run a smoke benchmark with
 * {@code ./gradlew :benchmarks:jmh -Pjmh.includes=io.micronaut.python.benchmark.PythonRequestBodyPoolBenchmark
 * -Pjmh.warmupIterations=1 -Pjmh.iterations=1}. Run the full pool-size sweep by adding
 * {@code -Pjmh.poolSizes=1,2,4,8}.</p>
 *
 * @since 5.2.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PythonRequestBodyPoolBenchmark {

    private static final String EXPECTED_BODY = "Ada:compiler:3:True:benchmark";
    private static final String JSON = """
        {"customer":"Ada","product":"compiler","quantity":3,"priority":true,"note":"benchmark"}
        """;

    /** Configured Python context pool size. */
    @Param({"4"})
    public int poolSize;

    private ApplicationContext applicationContext;
    private HttpClient client;
    private HttpRequest bodyRequest;
    private HttpRequest controlRequest;

    /** Starts the server and verifies both benchmark paths. */
    @Setup(Level.Trial)
    public void setUp() throws IOException, InterruptedException {
        try {
            applicationContext = ApplicationContext.builder()
                .properties(Map.of(
                    "micronaut.server.port", -1,
                    "micronaut.python.pool.enabled", true,
                    "micronaut.python.pool.size", poolSize,
                    "micronaut.python.pool.warn-wait", "60s"
                ))
                .start();
            warmPool();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();
            URI root = server.getURI().resolve("/python-request-body-benchmark/");
            client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            bodyRequest = HttpRequest.newBuilder(root.resolve("body"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON))
                .build();
            controlRequest = HttpRequest.newBuilder(root.resolve("control"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            verify(exchange(bodyRequest), EXPECTED_BODY);
            verify(exchange(controlRequest), "control");
        } catch (IOException | InterruptedException | RuntimeException e) {
            tearDown();
            throw e;
        }
    }

    /** Stops the benchmark application. */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    /** Measures request-body conversion with one JMH worker. */
    @Benchmark
    @Threads(1)
    public void bodyThreads1(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(bodyRequest));
    }

    /** Measures request-body conversion with two JMH workers. */
    @Benchmark
    @Threads(2)
    public void bodyThreads2(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(bodyRequest));
    }

    /** Measures request-body conversion with four JMH workers. */
    @Benchmark
    @Threads(4)
    public void bodyThreads4(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(bodyRequest));
    }

    /** Measures request-body conversion with eight JMH workers. */
    @Benchmark
    @Threads(8)
    public void bodyThreads8(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(bodyRequest));
    }

    /** Measures the pooled control endpoint with one JMH worker. */
    @Benchmark
    @Threads(1)
    public void controlThreads1(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(controlRequest));
    }

    /** Measures the pooled control endpoint with two JMH workers. */
    @Benchmark
    @Threads(2)
    public void controlThreads2(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(controlRequest));
    }

    /** Measures the pooled control endpoint with four JMH workers. */
    @Benchmark
    @Threads(4)
    public void controlThreads4(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(controlRequest));
    }

    /** Measures the pooled control endpoint with eight JMH workers. */
    @Benchmark
    @Threads(8)
    public void controlThreads8(Blackhole blackhole) throws IOException, InterruptedException {
        blackhole.consume(exchange(controlRequest));
    }

    private HttpResponse<String> exchange(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void warmPool() throws InterruptedException {
        PythonContextExecutor contextExecutor = applicationContext.getBean(PythonContextExecutor.class);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch acquired = new CountDownLatch(poolSize);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(poolSize);
        try {
            for (int i = 0; i < poolSize; i++) {
                futures.add(executor.submit(() -> contextExecutor.withContext(context -> {
                    acquired.countDown();
                    await(release);
                    return null;
                })));
            }
            if (!acquired.await(2, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out warming the Python context pool");
            }
            release.countDown();
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Failed to warm the Python context pool", e);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void verify(HttpResponse<String> response, String expected) {
        if (response.statusCode() != 200 || !expected.equals(response.body())) {
            throw new IllegalStateException("Unexpected benchmark response: " + response.statusCode() + " " + response.body());
        }
    }
}
