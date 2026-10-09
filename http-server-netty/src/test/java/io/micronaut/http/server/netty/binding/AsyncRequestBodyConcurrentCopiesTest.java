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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reads of the copies of an {@link AsyncRequestBody} that are in flight together: each
 * completes with its value. The body arrives in two pieces, and the reads are started before the
 * second one is sent, so every read is waiting for the body while the others are started.
 */
class AsyncRequestBodyConcurrentCopiesTest {
    private static final String SPEC_NAME = "AsyncRequestBodyConcurrentCopiesTest";
    private static final String BOUNDARY = "concurrent-copies";
    private static final String PERSON_AND_NAMED = "Person[name=Fred, age=42]|Named[name=Fred]";
    private static final String[] JSON = {"{\"name\":\"Fred\",", "\"age\":42}"};
    private static final String[] JSON_ARRAY = {"[{\"name\":\"Fred\",\"age\":42},", "{\"name\":\"Wilma\",\"age\":40}]"};
    private static final String[] URL_ENCODED = {"name=Fred&a", "ge=42"};
    private static final String MULTIPART_TYPE = MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY;
    private static final String[] MULTIPART = {
        "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"name\"\r\n"
            + "\r\n"
            + "Fred\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"age\"\r\n"
            + "\r\n"
            + "4",
        "2\r\n"
            + "--" + BOUNDARY + "--\r\n"
    };

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Gate gate;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        server = ctx.getBean(EmbeddedServer.class).start();
        gate = ctx.getBean(Gate.class);
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @Test
    void twoCopiesDecodeTheBodyTogether() throws Exception {
        assertEquals(PERSON_AND_NAMED, json("/concurrent/copies"));
    }

    @Test
    void theBodyAndACopyDecodeTheBodyTogether() throws Exception {
        assertEquals(PERSON_AND_NAMED, json("/concurrent/original-and-copy"));
        assertEquals(PERSON_AND_NAMED, json("/concurrent/copy-and-original"));
    }

    @Test
    void threeCopiesDecodeTheBodyTogether() throws Exception {
        assertEquals(PERSON_AND_NAMED + "|{name=Fred, age=42}", json("/concurrent/three"));
    }

    @Test
    void aCopyDecodesTheBodyWhileAnotherReadsItsText() throws Exception {
        assertEquals("Person[name=Fred, age=42]|" + JSON[0] + JSON[1], json("/concurrent/body-and-text"));
        assertEquals(JSON[0] + JSON[1] + "|Person[name=Fred, age=42]", json("/concurrent/text-and-body"));
    }

    @Test
    void aCopyDecodesTheBodyWhileTheBodyIsReadAsTextOrAsAForm() throws Exception {
        assertEquals("Person[name=Fred, age=42]|" + JSON[0] + JSON[1], json("/concurrent/copy-body-and-text"));
        assertEquals("{name=Fred, age=42}|Fred 42", exchange("/concurrent/copy-body-and-form", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED));
    }

    @Test
    void twoCopiesDecodeTheBodyTogetherInAFilterAndTheRouteReadsIt() throws Exception {
        assertEquals(PERSON_AND_NAMED + "|Person[name=Fred, age=42]", json("/concurrent/filter/body"));
        assertEquals(PERSON_AND_NAMED + "|Person[name=Fred, age=42]", json("/concurrent/filter/async"));
    }

    @Test
    void twoCopiesDecodeTheBodyFromDifferentThreads() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals(PERSON_AND_NAMED, json("/concurrent/threads"), "request " + i);
        }
    }

    @Test
    void theBodyAndACopyDecodeTheBodyFromDifferentThreads() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals(PERSON_AND_NAMED, json("/concurrent/threads-original"), "request " + i);
        }
    }

    @Test
    void twoCopiesDecodeTheBodyFromDifferentThreadsInAFilter() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals(PERSON_AND_NAMED + "|Person[name=Fred, age=42]", json("/concurrent/filter-threads/body"), "request " + i);
        }
    }

    @Test
    void twoCopiesReadTheFormTogether() throws Exception {
        assertEquals("Fred 42|Fred 42", exchange("/concurrent/forms", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED));
        assertEquals("Fred 42|Fred 42", exchange("/concurrent/forms", MULTIPART_TYPE, MULTIPART));
    }

    @Test
    void theBodyAndACopyReadTheFormTogether() throws Exception {
        assertEquals("Fred 42|Fred 42", exchange("/concurrent/form-original", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED));
        assertEquals("Fred 42|Fred 42", exchange("/concurrent/form-original", MULTIPART_TYPE, MULTIPART));
    }

    @Test
    void aCopyReadsTheFormWhileTheOthersReadThePartsAndDecodeTheBody() throws Exception {
        assertEquals("Fred 42|name=Fred,age=42|{name=Fred, age=42}",
            exchange("/concurrent/form-parts-body", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED));
    }

    @Test
    void twoCopiesReadTheFormFromDifferentThreads() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals("Fred 42|Fred 42", exchange("/concurrent/form-threads", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED), "request " + i);
        }
    }

    @Test
    void theBodyReadsTheFormWhileACopyDecodesTheBodyFromDifferentThreads() throws Exception {
        for (int i = 0; i < 100; i++) {
            String response = exchange("/concurrent/form-body-threads", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED);
            // the form consumes the body: the copy that is started before it decodes the whole
            // body, and the copy that is started after it is refused, as the body was already
            // read: it never decodes a part of the body
            if (!response.startsWith("HTTP/1.1 500 ")) {
                assertEquals("Fred 42|{name=Fred, age=42}", response, "request " + i);
            }
        }
    }

    @Test
    void twoCopiesReadThePartsTogether() throws Exception {
        assertEquals("name=Fred,age=42|name=Fred,age=42", exchange("/concurrent/parts", MULTIPART_TYPE, MULTIPART));
        assertEquals("name=Fred,age=42|name=Fred,age=42", exchange("/concurrent/parts", MediaType.APPLICATION_FORM_URLENCODED, URL_ENCODED));
    }

    @Test
    void twoCopiesReadTheElementsTogether() throws Exception {
        assertEquals("[Person[name=Fred, age=42], Person[name=Wilma, age=40]]|[Named[name=Fred], Named[name=Wilma]]",
            exchange("/concurrent/elements", MediaType.APPLICATION_JSON, JSON_ARRAY));
    }

    @Test
    void aCopyReadsTheElementsWhileAnotherDecodesTheBody() throws Exception {
        assertEquals("[Person[name=Fred, age=42], Person[name=Wilma, age=40]]|[Named[name=Fred], Named[name=Wilma]]",
            exchange("/concurrent/elements-and-body", MediaType.APPLICATION_JSON, JSON_ARRAY));
    }

    private static String json(String path) throws Exception {
        return exchange(path, MediaType.APPLICATION_JSON, JSON);
    }

    /**
     * Send the head of a request and the first piece of its body, then the second piece once the
     * reads of the body were started, and read the body of the response.
     */
    private static String exchange(String path, String contentType, String[] pieces) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            byte[] first = pieces[0].getBytes(StandardCharsets.UTF_8);
            byte[] second = pieces[1].getBytes(StandardCharsets.UTF_8);
            String head = "POST " + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + (first.length + second.length) + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
            gate.reset();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(first);
            out.flush();
            assertTrue(gate.awaitStarted(), "the reads were started before the body is complete: " + path);
            out.write(second);
            out.flush();
            return responseBody(socket.getInputStream());
        }
    }

    private static String responseBody(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            bytes.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        String head = bytes.toString(StandardCharsets.US_ASCII);
        String status = head.substring(0, Math.max(0, head.indexOf("\r\n")));
        String prefix = status.contains(" 200 ") ? "" : status + " ";
        for (String line : head.split("\r\n")) {
            if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
                return prefix + new String(in.readNBytes(Integer.parseInt(line.substring(15).trim())), StandardCharsets.UTF_8);
            }
        }
        return prefix + new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Introspected
    record Person(String name, int age) {
    }

    @Introspected
    record Named(String name) {
    }

    /**
     * Tells the test that the reads of a request were started.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Gate {
        private final Semaphore started = new Semaphore(0);

        void reset() {
            started.drainPermits();
        }

        void started() {
            started.release();
        }

        boolean awaitStarted() throws InterruptedException {
            return started.tryAcquire(20, TimeUnit.SECONDS);
        }

        /**
         * Start two reads together, on a thread each, and open the gate once both are started.
         */
        <A, B> CompletionStage<String> together(Supplier<CompletionStage<A>> first, Supplier<CompletionStage<B>> second) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            CompletableFuture<CompletionStage<A>> a = startOnThread(barrier, first);
            CompletableFuture<CompletionStage<B>> b = startOnThread(barrier, second);
            CompletableFuture<String> result = a.thenCompose(s -> s).thenCombine(b.thenCompose(s -> s), (x, y) -> x + "|" + y);
            CompletableFuture.allOf(a, b).whenComplete((ignored, error) -> started());
            return result;
        }

        private static <T> CompletableFuture<CompletionStage<T>> startOnThread(CyclicBarrier barrier, Supplier<CompletionStage<T>> read) {
            CompletableFuture<CompletionStage<T>> started = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(20, TimeUnit.SECONDS);
                    started.complete(read.get());
                } catch (Throwable e) {
                    started.completeExceptionally(e);
                }
            }, "concurrent-copy-read");
            thread.setDaemon(true);
            thread.start();
            return started;
        }
    }

    @ServerFilter("/concurrent")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CopyingFilters {
        private final Gate gate;

        CopyingFilters(Gate gate) {
            this.gate = gate;
        }

        @RequestFilter("/filter/**")
        CompletionStage<@Nullable HttpResponse<?>> copies(HttpRequest<?> request, AsyncRequestBody body) {
            CompletionStage<Person> person = body.copy().body(Person.class);
            CompletionStage<Named> named = body.copy().body(Named.class);
            gate.started();
            return person.thenCombine(named, (p, n) -> {
                request.setAttribute("copies", p + "|" + n);
                return null;
            });
        }

        @RequestFilter("/filter-threads/**")
        CompletionStage<@Nullable HttpResponse<?>> threads(HttpRequest<?> request, AsyncRequestBody body) {
            AsyncRequestBody first = body.copy();
            AsyncRequestBody second = body.copy();
            return gate.together(() -> first.body(Person.class), () -> second.body(Named.class)).thenApply(copies -> {
                request.setAttribute("copies", copies);
                return null;
            });
        }
    }

    @Controller("/concurrent")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CopiesController {
        private final Gate gate;

        CopiesController(Gate gate) {
            this.gate = gate;
        }

        @Post(uri = "/copies", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copies(AsyncRequestBody body) {
            CompletionStage<Person> person = body.copy().body(Person.class);
            CompletionStage<Named> named = body.copy().body(Named.class);
            gate.started();
            return person.thenCombine(named, (p, n) -> p + "|" + n);
        }

        @Post(uri = "/original-and-copy", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> originalAndCopy(AsyncRequestBody body) {
            AsyncRequestBody copy = body.copy();
            CompletionStage<Person> person = body.body(Person.class);
            CompletionStage<Named> named = copy.body(Named.class);
            gate.started();
            return person.thenCombine(named, (p, n) -> p + "|" + n);
        }

        @Post(uri = "/copy-and-original", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copyAndOriginal(AsyncRequestBody body) {
            CompletionStage<Named> named = body.copy().body(Named.class);
            CompletionStage<Person> person = body.body(Person.class);
            gate.started();
            return person.thenCombine(named, (p, n) -> p + "|" + n);
        }

        @Post(uri = "/three", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> three(AsyncRequestBody body) {
            CompletionStage<Person> person = body.copy().body(Person.class);
            CompletionStage<Named> named = body.copy().body(Named.class);
            CompletionStage<Map<String, Object>> map = body.copy().body(Argument.mapOf(String.class, Object.class));
            gate.started();
            return person.thenCombine(named, (p, n) -> p + "|" + n).thenCombine(map, (s, m) -> s + "|" + m);
        }

        @Post(uri = "/body-and-text", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> bodyAndText(AsyncRequestBody body) {
            CompletionStage<Person> person = body.copy().body(Person.class);
            CompletionStage<String> text = body.copy().text();
            gate.started();
            return person.thenCombine(text, (p, t) -> p + "|" + t);
        }

        @Post(uri = "/text-and-body", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> textAndBody(AsyncRequestBody body) {
            CompletionStage<String> text = body.copy().text();
            CompletionStage<Person> person = body.copy().body(Person.class);
            gate.started();
            return text.thenCombine(person, (t, p) -> t + "|" + p);
        }

        @Post(uri = "/copy-body-and-text", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copyBodyAndText(AsyncRequestBody body) {
            CompletionStage<Person> person = body.copy().body(Person.class);
            // moves the bytes of the request, while the copy waits for them
            CompletionStage<String> text = body.text();
            gate.started();
            return person.thenCombine(text, (p, t) -> p + "|" + t);
        }

        @Post(uri = "/copy-body-and-form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copyBodyAndForm(AsyncRequestBody body) {
            CompletionStage<Map<String, Object>> map = body.copy().body(Argument.mapOf(String.class, Object.class));
            CompletionStage<FormData> form = body.form();
            gate.started();
            return map.thenCombine(form, (m, f) -> m + "|" + describe(f));
        }

        @Post(uri = "/filter/body", produces = MediaType.TEXT_PLAIN)
        String filterBody(HttpRequest<?> request, @Body Person person) {
            return request.getAttribute("copies").orElse(null) + "|" + person;
        }

        @Post(uri = "/filter/async", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> filterAsync(HttpRequest<?> request, AsyncRequestBody body) {
            return body.body(Person.class).thenApply(person -> request.getAttribute("copies").orElse(null) + "|" + person);
        }

        @Post(uri = "/filter-threads/body", produces = MediaType.TEXT_PLAIN)
        String filterThreadsBody(HttpRequest<?> request, @Body Person person) {
            return request.getAttribute("copies").orElse(null) + "|" + person;
        }

        @Post(uri = "/threads", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> threads(AsyncRequestBody body) {
            AsyncRequestBody first = body.copy();
            AsyncRequestBody second = body.copy();
            return gate.together(() -> first.body(Person.class), () -> second.body(Named.class));
        }

        @Post(uri = "/threads-original", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> threadsOriginal(AsyncRequestBody body) {
            AsyncRequestBody copy = body.copy();
            return gate.together(() -> body.body(Person.class), () -> copy.body(Named.class));
        }

        @Post(uri = "/forms", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> forms(AsyncRequestBody body) {
            CompletionStage<FormData> first = body.copy().form();
            CompletionStage<FormData> second = body.copy().form();
            gate.started();
            return first.thenCombine(second, (a, b) -> describe(a) + "|" + describe(b));
        }

        @Post(uri = "/form-original", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> formOriginal(AsyncRequestBody body) {
            CompletionStage<FormData> first = body.copy().form();
            CompletionStage<FormData> second = body.form();
            gate.started();
            return first.thenCombine(second, (a, b) -> describe(a) + "|" + describe(b));
        }

        @Post(uri = "/form-parts-body", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> formPartsBody(AsyncRequestBody body) {
            CompletionStage<FormData> form = body.copy().form();
            CompletionStage<String> parts = describe(body.copy().parts());
            CompletionStage<Map<String, Object>> map = body.copy().body(Argument.mapOf(String.class, Object.class));
            gate.started();
            return form.thenCombine(parts, (f, p) -> describe(f) + "|" + p).thenCombine(map, (s, m) -> s + "|" + m);
        }

        @Post(uri = "/form-threads", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> formThreads(AsyncRequestBody body) {
            AsyncRequestBody first = body.copy();
            AsyncRequestBody second = body.copy();
            return gate.together(() -> first.form().thenApply(CopiesController::describe), () -> second.form().thenApply(CopiesController::describe));
        }

        @Post(uri = "/form-body-threads", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> formBodyThreads(AsyncRequestBody body) {
            AsyncRequestBody copy = body.copy();
            return gate.together(() -> body.form().thenApply(CopiesController::describe), () -> copy.body(Argument.mapOf(String.class, Object.class)));
        }

        @Post(uri = "/parts", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(AsyncRequestBody body) {
            CompletionStage<String> first = describe(body.copy().parts());
            CompletionStage<String> second = describe(body.copy().parts());
            gate.started();
            return first.thenCombine(second, (a, b) -> a + "|" + b);
        }

        @Post(uri = "/elements", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> elements(AsyncRequestBody body) {
            CompletionStage<List<Person>> people = collect(body.copy().elements(Person.class));
            CompletionStage<List<Named>> named = collect(body.copy().elements(Named.class));
            gate.started();
            return people.thenCombine(named, (p, n) -> p + "|" + n);
        }

        @Post(uri = "/elements-and-body", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> elementsAndBody(AsyncRequestBody body) {
            CompletionStage<List<Person>> people = collect(body.copy().elements(Person.class));
            CompletionStage<List<Named>> named = body.copy().body(Argument.listOf(Named.class));
            gate.started();
            return people.thenCombine(named, (p, n) -> p + "|" + n);
        }

        private static String describe(FormData form) {
            return form.getString("name") + " " + form.getString("age");
        }

        private static CompletionStage<String> describe(FormParts parts) {
            List<String> fields = new ArrayList<>();
            return parts.forEach(part -> part.text().thenAccept(text -> {
                synchronized (fields) {
                    fields.add(part.name() + "=" + text);
                }
            })).thenApply(ignored -> String.join(",", fields));
        }

        private static <T> CompletionStage<List<T>> collect(BodyElements<T> elements) {
            List<T> values = new ArrayList<>();
            return elements.forEach(value -> {
                values.add(value);
                return CompletableFuture.completedStage(null);
            }).thenApply(ignored -> values);
        }
    }
}
