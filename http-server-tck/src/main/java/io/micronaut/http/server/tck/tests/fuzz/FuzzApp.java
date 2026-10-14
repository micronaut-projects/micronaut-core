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
package io.micronaut.http.server.tck.tests.fuzz;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Beans of the fuzz suite.
 */
final class FuzzApp {
    static final String SPEC = "HttpServerFuzzTest";
    static final AtomicInteger CANCELLED = new AtomicInteger();
    static final AtomicInteger STARTED = new AtomicInteger();
    static final int MAX = 64 * 1024 * 1024;

    private FuzzApp() {
    }

    static String d(byte[] b) {
        return RawHttp.describe(b);
    }

    static byte[] pattern(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ((i * 31 + (i >>> 8) * 7) & 0xff);
        }
        return b;
    }

    @Introspected
    @ReflectiveAccess
    record Person(String name, int age) {
    }

    @Introspected
    @ReflectiveAccess
    record Item(String name) {
    }

    @Controller("/f")
    @Requires(property = "spec.name", value = SPEC)
    @Consumes(MediaType.ALL)
    @Produces(MediaType.TEXT_PLAIN)
    static class BodyController {

        @Post("/string")
        String string(@Body String s) {
            return d(s.getBytes(StandardCharsets.UTF_8));
        }

        @Post("/string-opt")
        String stringOpt(@Nullable @Body String s) {
            return s == null ? "null" : d(s.getBytes(StandardCharsets.UTF_8));
        }

        @Post("/bytes")
        String bytes(@Body byte[] b) {
            return d(b);
        }

        @Post("/is")
        @ExecuteOn(TaskExecutors.BLOCKING) // reading a blocking stream on an event loop is rejected by Netty
        String is(@Body InputStream in) throws IOException {
            try (in) { // the route owns the body stream and must close it
                return d(in.readAllBytes());
            }
        }

        @Post("/pub")
        Mono<String> pub(@Body Publisher<byte[]> p) {
            return Flux.from(p).reduce(new byte[0], (a, b) -> {
                byte[] r = new byte[a.length + b.length];
                System.arraycopy(a, 0, r, 0, a.length);
                System.arraycopy(b, 0, r, a.length, b.length);
                return r;
            }).map(FuzzApp::d);
        }

        @Post("/mono")
        Mono<String> mono(@Body Mono<byte[]> p) {
            return p.map(FuzzApp::d).defaultIfEmpty("empty");
        }

        @Post("/future")
        CompletableFuture<String> future(@Body CompletableFuture<byte[]> p) {
            return p.thenApply(FuzzApp::d);
        }

        @Post("/pojo")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Person pojo(@Body Person p) {
            return p;
        }

        @Post("/map")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, Object> map(@Body Map<String, Object> m) {
            return m;
        }

        @Post("/json-args")
        @Consumes(MediaType.APPLICATION_JSON)
        String jsonArgs(String name, int age) {
            return name + ":" + age;
        }

        @Post("/form-args")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        String formArgs(String a, @Nullable List<String> b, @Nullable String c) {
            return "a=" + a + ";b=" + b + ";c=" + c;
        }

        @Post("/form-map")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        String formMap(@Body Map<String, Object> m) {
            return new TreeMap<>(m).toString();
        }

        @Post("/form-pojo")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        String formPojo(@Body Person p) {
            return p.name() + ":" + p.age();
        }

        @Post("/mp-basic")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        String mpBasic(@Part("text") String text, CompletedFileUpload file) throws IOException {
            return text + "|" + file.getFilename() + "|" + file.getContentType().map(Object::toString).orElse("-") + "|" + d(file.getBytes());
        }

        @Post("/mp-multi")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Mono<String> mpMulti(Publisher<CompletedFileUpload> file) {
            return Flux.from(file).map(f -> {
                try {
                    return f.getFilename() + "|" + f.getContentType().map(Object::toString).orElse("-") + "|" + d(f.getBytes());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).collectList().map(l -> String.join(";", l));
        }

        @Post("/mp-streaming")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Mono<String> mpStreaming(StreamingFileUpload file) {
            long[] n = new long[1];
            java.io.OutputStream counting = new java.io.OutputStream() {
                @Override
                public void write(int b) {
                    n[0]++;
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    n[0] += len;
                }
            };
            return Flux.from(file.transferTo(counting)).then(Mono.fromSupplier(() -> file.getFilename() + "|" + n[0]));
        }

        @Post("/mp-partdata")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Mono<String> mpPartData(Publisher<PartData> file) {
            return Flux.from(file).reduce(0L, (n, pd) -> {
                return n + pd.getBytes().length;
            }).map(n -> "pd|" + n);
        }

        @Post("/ignore")
        @Consumes(MediaType.ALL)
        String ignore() {
            return "ignored";
        }
    }

    @Controller("/r")
    @Requires(property = "spec.name", value = SPEC)
    static class ResponseController {

        @Get("/plain")
        @Produces(MediaType.TEXT_PLAIN)
        String plain() {
            return "hello";
        }

        @Head("/head")
        @Produces(MediaType.TEXT_PLAIN)
        String head() {
            return "hello-head";
        }

        @Get("/head")
        @Produces(MediaType.TEXT_PLAIN)
        String getHead() {
            return "hello-head";
        }

        @Get("/json")
        Person json() {
            return new Person("n", 1);
        }

        @Get("/empty")
        HttpResponse<?> empty() {
            return HttpResponse.ok();
        }

        @Get("/large")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        byte[] large(@QueryValue int n) {
            return pattern(n);
        }

        @Get("/flux-json")
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Item> fluxJson(@QueryValue int n) {
            return Flux.range(0, n).map(i -> new Item("i" + i));
        }

        @Get("/flux-stream")
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        Flux<Item> fluxStream(@QueryValue int n) {
            return Flux.range(0, n).map(i -> new Item("i" + i));
        }

        @Get("/flux-bytes")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        Flux<byte[]> fluxBytes(@QueryValue int n) {
            return Flux.range(0, n).map(i -> pattern(1000));
        }

        @Get("/sse")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> sse(@QueryValue int n) {
            return Flux.range(0, n).map(i -> Event.of("e" + i).id("id" + i));
        }

        @Get("/err-before")
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Item> errBefore() {
            return Flux.error(new IllegalStateException("boom"));
        }

        @Get("/err-after")
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Item> errAfter(@QueryValue int n) {
            return Flux.concat(Flux.range(0, n).map(i -> new Item("i" + i)), Flux.error(new IllegalStateException("boom")));
        }

        @Get("/err-after-stream")
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        Flux<Item> errAfterStream(@QueryValue int n) {
            return Flux.concat(Flux.range(0, n).map(i -> new Item("i" + i)), Flux.error(new IllegalStateException("boom")));
        }

        @Get("/err-sync")
        String errSync() {
            throw new IllegalStateException("boom");
        }

        @Get("/streamed-file")
        StreamedFile streamedFile(@QueryValue int n) {
            return new StreamedFile(new ByteArrayInputStream(pattern(n)), MediaType.APPLICATION_OCTET_STREAM_TYPE);
        }

        @Get("/streamed-file-len")
        StreamedFile streamedFileLen(@QueryValue int n) {
            return new StreamedFile(new ByteArrayInputStream(pattern(n)), MediaType.APPLICATION_OCTET_STREAM_TYPE, System.currentTimeMillis(), n);
        }

        @Get("/infinite")
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        Flux<Item> infinite() {
            STARTED.incrementAndGet();
            return Flux.interval(Duration.ofMillis(5)).onBackpressureDrop().map(i -> new Item("x".repeat(100) + i)).doOnCancel(CANCELLED::incrementAndGet);
        }

        @Get("/infinite-sse")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> infiniteSse() {
            STARTED.incrementAndGet();
            return Flux.interval(Duration.ofMillis(5)).onBackpressureDrop().map(i -> Event.of("x" + i)).doOnCancel(CANCELLED::incrementAndGet);
        }

        @Get("/counters")
        @Produces(MediaType.TEXT_PLAIN)
        String counters() {
            return STARTED.get() + " " + CANCELLED.get();
        }

        @Get("/slow")
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        Flux<Item> slow(@QueryValue int n) {
            return Flux.range(0, n).delayElements(Duration.ofMillis(20)).map(i -> new Item("s" + i));
        }
    }

    @Controller("/flt")
    @Requires(property = "spec.name", value = SPEC)
    @Consumes(MediaType.ALL)
    @Produces(MediaType.TEXT_PLAIN)
    static class FilteredController {
        @Post("/opt")
        String opt(@Nullable @Body String body) {
            return body == null ? "body null" : "body " + body;
        }

        @Post("/req")
        String req(@Body String body) {
            return "body " + body;
        }

        @Post("/bytes")
        String bytes(@Body byte[] body) {
            return "bytes " + d(body);
        }

        @Post("/form")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        String form(@Body Map<String, Object> form) {
            return "form " + new TreeMap<>(form);
        }

        @Get("/target")
        String target(HttpRequest<?> request) {
            return "target " + request.getPath() + " " + request.getParameters().get("q");
        }
    }

    @ServerFilter("/flt/**")
    @Requires(property = "spec.name", value = SPEC)
    static class BodyFilters {
        @RequestFilter
        @Nullable
        HttpRequest<?> filter(HttpRequest<?> request, @Nullable @Body String ignored) {
            String mode = request.getHeaders().get("x-mode");
            if (mode == null) {
                return null;
            }
            return switch (mode) {
                case "read" -> {
                    request.setAttribute("filter-read", ignored == null ? -1 : ignored.length());
                    yield null;
                }
                case "replace" -> request.mutate().body("replacement");
                case "clear" -> request.mutate().body(null);
                case "wrap" -> new HttpRequestWrapper<>(request);
                default -> null;
            };
        }
    }

    @ServerFilter("/flt/**")
    @Requires(property = "spec.name", value = SPEC)
    static class MutateOnlyFilters {
        @RequestFilter
        @Nullable
        HttpRequest<?> filter(HttpRequest<?> request) {
            String mode = request.getHeaders().get("x-mode2");
            if (mode == null) {
                return null;
            }
            return switch (mode) {
                case "replace" -> request.mutate().body("replacement");
                case "clear" -> request.mutate().body(null);
                case "wrap" -> new HttpRequestWrapper<>(request);
                case "uri" -> request.mutate().uri(java.net.URI.create(request.getPath() + "?changed=1"));
                default -> null;
            };
        }
    }

    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC)
    static class PreMatchingFilters {
        @RequestFilter
        @io.micronaut.http.server.annotation.PreMatching
        @Nullable
        HttpRequest<?> pre(HttpRequest<?> request) {
            if (request.getPath().startsWith("/pre/")) {
                return request.mutate().uri(java.net.URI.create("/flt/target?q=" + request.getPath().substring(5)));
            }
            return null;
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class Routes {
        @Singleton
        @Named("fuzz-fn")
        HttpRoutes routes() {
            return routes -> {
                routes.POST("/fn/text").consumesAll().body().handleAsync((request, vars, body) ->
                    body.text().thenApply(t -> HttpResponse.ok(d(t.getBytes(StandardCharsets.UTF_8))).contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.POST("/fn/bytes").consumesAll().body().handleAsync((request, vars, body) ->
                    body.bytes(MAX).thenApply(t -> HttpResponse.ok(d(t)).contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.POST("/fn/bytes-small").consumesAll().body().handleAsync((request, vars, body) ->
                    body.bytes(1000).thenApply(t -> HttpResponse.ok(d(t)).contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.POST("/fn/copy").consumesAll().body().handleAsync((request, vars, body) -> {
                    var copy = body.copy();
                    CompletionStage<byte[]> c = copy.bytes(MAX);
                    CompletionStage<byte[]> o = body.bytes(MAX);
                    return c.thenCombine(o, (cb, ob) -> HttpResponse.ok("copy=" + d(cb) + " orig=" + d(ob)).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.POST("/fn/copy-orig-first").consumesAll().body().handleAsync((request, vars, body) -> {
                    var copy = body.copy();
                    CompletionStage<byte[]> o = body.bytes(MAX);
                    CompletionStage<byte[]> c = copy.bytes(MAX);
                    return c.thenCombine(o, (cb, ob) -> HttpResponse.ok("copy=" + d(cb) + " orig=" + d(ob)).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.POST("/fn/copy-sequential").consumesAll().body().handleAsync((request, vars, body) -> {
                    var copy = body.copy();
                    return copy.bytes(MAX).thenCompose(cb -> body.bytes(MAX).thenApply(ob ->
                        HttpResponse.ok("copy=" + d(cb) + " orig=" + d(ob)).contentType(MediaType.TEXT_PLAIN_TYPE)));
                });
                routes.POST("/fn/transfer").consumesAll().body().handleAsync((request, vars, body) -> {
                    try {
                        Path p = Files.createTempDirectory("fuzz").resolve("out.bin");
                        return body.transferTo(p).thenApply(v -> {
                            try {
                                byte[] b = Files.readAllBytes(p);
                                Files.delete(p);
                                return HttpResponse.ok(d(b)).contentType(MediaType.TEXT_PLAIN_TYPE);
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
                routes.POST("/fn/json").consumes(MediaType.APPLICATION_JSON_TYPE).body().handleAsync((request, vars, body) ->
                    body.body(Person.class).thenApply(p -> HttpResponse.ok(p).contentType(MediaType.APPLICATION_JSON_TYPE)));
                routes.POST("/fn/elements").consumes(MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_STREAM_TYPE).body().handleAsync((request, vars, body) -> {
                    List<String> names = Collections.synchronizedList(new ArrayList<>());
                    return body.elements(Item.class).forEach(i -> {
                        names.add(i.name());
                        return CompletableFuture.completedFuture(null);
                    }).thenApply(v -> HttpResponse.ok(String.join(",", names)).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.POST("/fn/form").consumesAll().body().handleAsync((request, vars, body) ->
                    body.form().thenApply(fd -> {
                        try (fd) {
                            StringBuilder sb = new StringBuilder();
                            for (String n : new java.util.TreeSet<>(fd.names())) {
                                sb.append(n).append('=').append(fd.getValues(n)).append(';');
                            }
                            return HttpResponse.ok(sb.toString()).contentType(MediaType.TEXT_PLAIN_TYPE);
                        }
                    }));
                routes.POST("/fn/formdata-files").consumesAll().body().handleAsync((request, vars, body) ->
                    body.form().thenCompose(fd -> {
                        List<CompletionStage<String>> parts = new ArrayList<>();
                        for (String n : request.getParameters().getAll("f")) {
                            for (var f : fd.getFiles(n)) {
                                parts.add(f.bytes(MAX).thenApply(b -> n + "|" + f.fileName() + "|" + f.contentType().map(Object::toString).orElse("-") + "|" + d(b)));
                            }
                        }
                        return CompletableFuture.allOf(parts.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenApply(v -> {
                                StringBuilder sb = new StringBuilder();
                                for (String n : new java.util.TreeSet<>(fd.names())) {
                                    sb.append(n).append('=').append(fd.getValues(n)).append(';');
                                }
                                for (var p : parts) {
                                    sb.append(p.toCompletableFuture().join()).append(';');
                                }
                                fd.close();
                                return HttpResponse.ok(sb.toString()).contentType(MediaType.TEXT_PLAIN_TYPE);
                            });
                    }));
                routes.POST("/fn/parts").consumesAll().body().handleAsync((request, vars, body) -> {
                    List<String> out = Collections.synchronizedList(new ArrayList<>());
                    return body.parts().forEach(part ->
                        part.bytes(MAX).thenAccept(b -> out.add(part.name() + "|" + part.fileName() + "|" + part.contentType().map(Object::toString).orElse("-") + "|" + d(b)))
                    ).thenApply(v -> HttpResponse.ok(String.join("\n", out)).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.POST("/fn/unread").consumesAll().handleAsync((request, vars) ->
                    CompletableFuture.completedFuture(HttpResponse.accepted()));
                routes.POST("/fn/discard").consumesAll().body().handleAsync((request, vars, body) ->
                    body.discardBody().thenApply(v -> HttpResponse.ok("discarded").contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.POST("/fn/text-then-bytes").consumesAll().body().handleAsync((request, vars, body) ->
                    body.text().thenApply(t -> HttpResponse.ok("t" + t.length()).contentType(MediaType.TEXT_PLAIN_TYPE)));
            };
        }
    }
}
