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
package io.micronaut.http.server.netty.handler;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.netty.JfrSupport;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks, each in a fresh JVM, that serving requests neither needs the {@code jdk.jfr} module
 * nor initializes JFR, and that a recording started after the first request still gets the
 * events, with requests still served once it stops.
 */
class JfrEventGatingTest {
    private static final String SPEC_NAME = "JfrEventGatingTest";
    private static final String RESULT = "RESULT ";
    private static final String METADATA_REPOSITORY = "jdk.jfr.internal.MetadataRepository ";

    @ParameterizedTest
    @EnumSource(Transport.class)
    void firstRequestDoesNotInitializeJfr(Transport transport) {
        // Netty's own JFR events initialize JFR on the first buffer allocation until it gates
        // them on an initialized Flight Recorder too (netty/netty#17612).
        ChildJvm child = ChildJvm.run(transport, Scenario.FIRST_REQUEST, "-Xlog:class+load", "-Dio.netty.jfr.enabled=false");

        assertEquals("ok", child.result("body"), child::summary);
        assertEquals("false", child.result("recorderInitialized"), child::summary);
        assertEquals("false", child.result("jfrSupport"), child::summary);
        assertFalse(child.output().contains(METADATA_REPOSITORY), () -> METADATA_REPOSITORY + "was loaded\n" + child.summary());
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void requestSucceedsWithoutJfrModule(Transport transport) {
        ChildJvm child = ChildJvm.run(transport, Scenario.NO_JFR_MODULE, "--limit-modules", "java.se,jdk.unsupported,jdk.zipfs");

        assertEquals("false", child.result("jfrAvailable"), child::summary);
        assertEquals("false", child.result("jfrSupport"), child::summary);
        assertEquals("ok", child.result("body"), child::summary);
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void recordingStartedAfterFirstRequestReceivesEvents(Transport transport) {
        ChildJvm child = ChildJvm.run(transport, Scenario.LATE_RECORDING);

        assertEquals("false", child.result("recorderInitialized"), child::summary);
        for (String eventName : transport.eventNames) {
            assertEquals(transport.expectedEvent, child.result("event:" + eventName), child::summary);
        }
        assertEquals("true", child.result("jfrSupportAfterRecording"), child::summary);
        assertEquals("ok", child.result("bodyAfterRecording"), child::summary);
    }

    @SuppressWarnings("ImmutableEnumChecker") // Map.of values and arrays nothing writes to
    enum Transport {
        HTTP1(
            Map.of(),
            "/jfr-gating",
            "GET /jfr-gating 200",
            "io.micronaut.http.server.netty.handler.Http1RequestEvent"
        ),
        HTTP2(
            Map.of(
                "micronaut.server.ssl.enabled", true,
                "micronaut.server.ssl.build-self-signed", true,
                "micronaut.server.ssl.port", -1,
                "micronaut.server.http-version", "2.0",
                "micronaut.http.client.ssl.insecure-trust-all-certificates", true
            ),
            "/jfr-gating",
            "GET /jfr-gating 200",
            "io.micronaut.http.server.netty.handler.Http2RequestEvent"
        ),
        LOOM_CARRIER(
            Map.of(
                "micronaut.netty.event-loops.default.loom-carrier", true,
                "micronaut.netty.event-loops.default.num-threads", 1,
                "micronaut.netty.loom-carrier.normal-warmup-tasks", 0
            ),
            "/jfr-gating/blocking",
            "recorded",
            "io.micronaut.http.netty.channel.loom.LoomCarrierGroup$ContinuationScheduled",
            "io.micronaut.http.netty.channel.loom.LoomCarrierGroup$LoopTick"
        );

        // Event names are strings: loading an event class needs jdk.jfr.
        final Map<String, Object> properties;
        final String path;
        final String expectedEvent;
        final String[] eventNames;

        Transport(Map<String, Object> properties, String path, String expectedEvent, String... eventNames) {
            this.properties = properties;
            this.path = path;
            this.expectedEvent = expectedEvent;
            this.eventNames = eventNames;
        }
    }

    enum Scenario {
        FIRST_REQUEST,
        NO_JFR_MODULE,
        LATE_RECORDING
    }

    private record ChildJvm(String output, Map<String, String> results) {
        static ChildJvm run(Transport transport, Scenario scenario, String... jvmArgs) {
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            command.addAll(coverageAgent());
            command.addAll(Arrays.asList(jvmArgs));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(App.class.getName());
            command.add(transport.name());
            command.add(scenario.name());
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
                if (!process.waitFor(1, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                }
                String text = output.get(30, TimeUnit.SECONDS);
                Map<String, String> results = text.lines()
                    .filter(line -> line.startsWith(RESULT))
                    .map(line -> line.substring(RESULT.length()).split("=", 2))
                    .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1], (a, b) -> b));
                return new ChildJvm(text, results);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to run the child JVM", e);
            }
        }

        /**
         * @return the JaCoCo agent of this JVM, if any, so that the code run by the child JVM
         *     counts towards the coverage of the build
         */
        private static List<String> coverageAgent() {
            return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:") && arg.contains("jacoco"))
                .toList();
        }

        private static String readAll(InputStream in) {
            try (in) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                in.transferTo(bytes);
                return bytes.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String result(String key) {
            return results.get(key);
        }

        /**
         * @return the output without the unified JVM logging, which can be thousands of lines
         */
        String summary() {
            return output.lines()
                .filter(line -> !line.startsWith("[") || !line.contains("][info]["))
                .collect(Collectors.joining("\n"));
        }
    }

    /**
     * The application run by the child JVM.
     */
    public static final class App {
        public static void main(String[] args) throws Exception {
            Transport transport = Transport.valueOf(args[0]);
            Scenario scenario = Scenario.valueOf(args[1]);
            Map<String, Object> properties = new HashMap<>(transport.properties);
            properties.put("spec.name", SPEC_NAME);
            try (ApplicationContext ctx = ApplicationContext.run(properties)) {
                EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
                server.start();
                try (HttpClient httpClient = ctx.createBean(HttpClient.class, server.getURL())) {
                    BlockingHttpClient client = httpClient.toBlocking();
                    if (scenario == Scenario.NO_JFR_MODULE) {
                        result("jfrAvailable", NativeImageUtils.JFR_AVAILABLE);
                        result("jfrSupport", JfrSupport.isRecorderInitialized());
                        result("body", client.retrieve(transport.path));
                    } else {
                        result("body", client.retrieve(transport.path));
                        result("recorderInitialized", Jfr.recorderInitialized());
                        result("jfrSupport", JfrSupport.isRecorderInitialized());
                        if (scenario == Scenario.LATE_RECORDING) {
                            Jfr.recordEvents(() -> client.retrieve(transport.path), transport.eventNames);
                            // The Flight Recorder outlives the recording, whose events are off again
                            result("jfrSupportAfterRecording", JfrSupport.isRecorderInitialized());
                            result("bodyAfterRecording", client.retrieve(transport.path));
                        }
                    }
                }
            }
            System.exit(0);
        }

        static void result(String key, Object value) {
            System.out.println(RESULT + key + "=" + value);
        }
    }

    /**
     * Keeps every use of {@code jdk.jfr} out of {@link App}, which also runs without that module.
     */
    private static final class Jfr {
        static boolean recorderInitialized() {
            return FlightRecorder.isInitialized();
        }

        static void recordEvents(Runnable action, String... eventNames) throws InterruptedException {
            Map<String, String> received = new ConcurrentHashMap<>();
            CountDownLatch allReceived = new CountDownLatch(eventNames.length);
            try (RecordingStream stream = new RecordingStream()) {
                for (String eventName : eventNames) {
                    stream.enable(eventName);
                    stream.onEvent(eventName, event -> {
                        if (received.putIfAbsent(eventName, describe(event)) == null) {
                            allReceived.countDown();
                        }
                    });
                }
                stream.startAsync();
                action.run();
                if (!allReceived.await(30, TimeUnit.SECONDS)) {
                    System.out.println("Timed out waiting for the JFR events");
                }
            }
            for (String eventName : eventNames) {
                App.result("event:" + eventName, received.getOrDefault(eventName, "missing"));
            }
        }

        private static String describe(RecordedEvent event) {
            if (event.hasField("uri")) {
                return event.getString("method") + " " + event.getString("uri") + " " + event.getInt("status");
            }
            return "recorded";
        }
    }

    @Controller("/jfr-gating")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class GatingController {
        @Get
        public String get() {
            return "ok";
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/blocking")
        public String blocking() {
            return "ok";
        }
    }
}
