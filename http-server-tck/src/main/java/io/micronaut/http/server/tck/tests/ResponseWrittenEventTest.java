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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.context.event.HttpResponseWrittenEvent;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HttpResponseWrittenEvent}, verifying that the event is published
 * exactly once for each request with the correct status and byte count.
 *
 * @since 5.3.0
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class ResponseWrittenEventTest {
    public static final String SPEC_NAME = "ResponseWrittenEventTest";

    @Test
    void fixedResponseReportsStatusAndBytes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/ok"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/ok");
                assertEquals("/response-written/ok", event.request().getPath());
                assertEquals(HttpStatus.OK, event.status());
                assertEquals("hello".length(), event.bytesWritten());
            });
    }

    @Test
    void emptyResponseReportsZeroBytes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/empty"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/empty");
                assertEquals(HttpStatus.OK, event.status());
                assertEquals(0, event.bytesWritten());
            });
    }

    @Test
    void noContentResponseReportsZeroBytes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/no-content"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.NO_CONTENT).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/no-content");
                assertEquals(HttpStatus.NO_CONTENT, event.status());
                assertEquals(0, event.bytesWritten());
            });
    }

    @Test
    void customStatusIsReported() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/created"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.CREATED).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/created");
                assertEquals(HttpStatus.CREATED, event.status());
                assertTrue(event.bytesWritten() > 0);
            });
    }

    @Test
    void streamingResponseReportsTotalBytes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/stream"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/stream");
                assertEquals(HttpStatus.OK, event.status());
                // the streaming body is 100_000 bytes of 'A'
                assertEquals(100_000, event.bytesWritten());
            });
    }

    @Test
    void errorResponseReportsErrorStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/error"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/error");
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, event.status());
                assertTrue(event.bytesWritten() >= 0);
            });
    }

    @Test
    void notFoundReportsStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/nonexistent"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.NOT_FOUND).build());
                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/nonexistent");
                assertEquals(HttpStatus.NOT_FOUND, event.status());
                assertTrue(event.bytesWritten() >= 0);
            });
    }

    @Test
    void exactlyOnceDelivery() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/ok"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                awaitSingleEvent(recorder, "/response-written/ok — exactly-once");
                // verify no extra events arrive after a grace period
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
                assertEquals(1, recorder.events.size(),
                    "expected exactly one HttpResponseWrittenEvent");
            });
    }

    @Test
    void headRequestReportsNoBodyBytes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.HEAD("/response-written/ok"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                RecordedEvent event = awaitSingleEvent(recorder, "HEAD /response-written/ok");
                assertEquals(HttpStatus.OK, event.status());
                assertEquals(0, event.bytesWritten(), "HEAD responses have no body bytes to count");
            });
    }

    @Test
    void eachRequestReportsItsOwnEvent() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/ok"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                awaitEventCount(recorder, 1, "first request");

                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                awaitEventCount(recorder, 2, "second request");

                assertTrue(recorder.events.stream().allMatch(event -> event.status() == HttpStatus.OK),
                    "both events must report the response status");
            });
    }

    @Test
    void customStatusCodeIsReportedWithoutFailingTheResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-written/custom-status"),
            (server, request) -> {
                EventRecorder recorder = recorder(server);
                HttpResponse<String> response = server.exchange(request, String.class);
                assertEquals(299, response.code());
                assertEquals("custom-status-body", response.body());

                RecordedEvent event = awaitSingleEvent(recorder, "/response-written/custom-status");
                // HttpStatus is an enum and has no constant for 299. The response and the event
                // must still be produced; the status is simply unavailable.
                assertNull(event.status());
                assertEquals("custom-status-body".length(), event.bytesWritten());
            });
    }

    private static EventRecorder recorder(ServerUnderTest server) {
        EventRecorder recorder = server.getApplicationContext().getBean(EventRecorder.class);
        recorder.events.clear();
        return recorder;
    }

    private static RecordedEvent awaitSingleEvent(EventRecorder recorder, String context) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (recorder.events.isEmpty() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        // short grace period to catch duplicate events
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
        assertEquals(1, recorder.events.size(),
            () -> "expected exactly one HttpResponseWrittenEvent for " + context
                + " but got " + recorder.events.size());
        RecordedEvent event = recorder.events.get(0);
        assertNotNull(event.request(), "event source must be the request");
        return event;
    }

    private static void awaitEventCount(EventRecorder recorder, int expected, String context) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (recorder.events.size() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        // short grace period to catch extra events
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
        assertEquals(expected, recorder.events.size(),
            () -> "expected " + expected + " HttpResponseWrittenEvent(s) for " + context
                + " but got " + recorder.events.size());
    }

    // ------ Infrastructure ------

    record RecordedEvent(
        io.micronaut.http.HttpRequest<?> request,
        HttpStatus status,
        long bytesWritten
    ) { }

    @Controller("/response-written")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestController {

        @Get("/ok")
        @Produces(MediaType.TEXT_PLAIN)
        String ok() {
            return "hello";
        }

        @Get("/empty")
        @Produces(MediaType.TEXT_PLAIN)
        String empty() {
            return "";
        }

        @Get("/no-content")
        @Status(HttpStatus.NO_CONTENT)
        void noContent() {
        }

        @Get("/created")
        HttpResponse<String> created() {
            return HttpResponse.created("created-body");
        }

        @Get("/stream")
        @Produces(MediaType.TEXT_PLAIN)
        InputStream stream() {
            byte[] data = new byte[100_000];
            java.util.Arrays.fill(data, (byte) 'A');
            return new java.io.ByteArrayInputStream(data);
        }

        @Get("/error")
        @Produces(MediaType.TEXT_PLAIN)
        String error() {
            throw new IllegalStateException("test error");
        }

        @Get("/custom-status")
        HttpResponse<String> customStatus() {
            return HttpResponse.status(299, "Custom Status").body("custom-status-body");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EventRecorder implements ApplicationEventListener<HttpResponseWrittenEvent> {
        final CopyOnWriteArrayList<RecordedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(HttpResponseWrittenEvent event) {
            events.add(new RecordedEvent(
                event.getSource(),
                event.getStatus(),
                event.getBytesWritten()));
        }
    }
}
