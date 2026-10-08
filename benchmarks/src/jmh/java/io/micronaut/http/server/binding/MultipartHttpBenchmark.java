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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Loopback HTTP multipart latency including transport, parsing, binding, storage and consumption.
 * Use JMH {@code -t 1} and {@code -t 4} for separate serial and concurrent upload measurements.
 * This establishes current-path costs, not a previous-release performance comparison.
 */
@Internal
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
public class MultipartHttpBenchmark {
    @Param({"memory", "mixed"})
    public String storage;
    @Param({"16384", "262144"})
    public int size;
    @Param({"fast", "slow"})
    public String consumer;
    private ApplicationContext context;
    private HttpClient client;
    private HttpRequest request;
    private String expected;

    /** Start a dedicated random-port server outside measurement. */
    @Setup
    public void setup() throws IOException, InterruptedException {
        context = ApplicationContext.run(Map.of(
            "spec.name", "MultipartHttpBenchmark",
            "micronaut.server.port", -1,
            "micronaut.server.multipart.mixed", storage.equals("mixed"),
            "micronaut.server.multipart.threshold", 1024,
            "micronaut.server.multipart.max-file-size", "1MB",
            "micronaut.server.max-request-size", "2MB"));
        EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(("--benchmark-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(new byte[size]);
        bytes.write("\r\n--benchmark-boundary--\r\n".getBytes(StandardCharsets.US_ASCII));
        request = HttpRequest.newBuilder(URI.create(server.getURL() + "/benchmark-upload/" + consumer))
            .header("Content-Type", "multipart/form-data; boundary=benchmark-boundary")
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        expected = (storage.equals("mixed") ? "disk:" : "memory:") + size;
        upload(); // Verify that configured spill and complete consumption really occur.
    }

    /** Stop the server outside measurement. */
    @TearDown
    public void close() {
        client.close();
        context.close();
    }

    /** @return The storage kind and consumed byte count */
    @Benchmark
    public String upload() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().equals(expected)) {
            throw new IllegalStateException("Unexpected upload response: " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    /** Benchmark-only consumers of the released blocking upload API, on a blocking executor. */
    @Internal
    @Controller("/benchmark-upload")
    @Requires(property = "spec.name", value = "MultipartHttpBenchmark")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public static class UploadController {
        /** @param file The completed upload @return Its storage kind and size */
        @Post(value = "/fast", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        public String fast(@Part("file") CompletedFileUpload file) throws IOException {
            return consume(file, false);
        }

        /** @param file The completed upload @return Its storage kind and size */
        @Post(value = "/slow", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        public String slow(@Part("file") CompletedFileUpload file) throws IOException {
            return consume(file, true);
        }

        private String consume(CompletedFileUpload file, boolean slow) throws IOException {
            String storage = file.isInMemory() ? "memory:" : "disk:";
            try (file; InputStream input = file.getInputStream()) {
                byte[] chunk = new byte[4096];
                long read = 0;
                int count;
                while ((count = input.read(chunk)) != -1) {
                    read += count;
                    if (slow) {
                        LockSupport.parkNanos(100_000);
                    }
                }
                return storage + read;
            }
        }
    }
}
