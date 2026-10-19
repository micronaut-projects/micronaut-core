package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.binders.StreamedNettyRequestArgumentBinder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A filter that continues with the mutable view of the Netty request, {@link HttpRequest#mutate()}:
 * the route sees the connection of the request, and the body of the request is bound, unless the
 * filter set the body of the view, even to {@code null}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilterMutableViewTest {
    static final String SPEC_NAME = "FilterMutableViewTest";
    private static final String MUTATE = "X-Mutate";
    private static final String BODY = "X-Body";
    private static final String LATE_BODY = "X-Late-Body";

    private EmbeddedServer server;
    private HttpClient client;

    @BeforeAll
    void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURI());
    }

    @AfterAll
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void theViewHasTheConnectionOfTheRequest() {
        String direct = exchange(HttpRequest.GET("/mv/target/info"));
        assertEquals(direct.replace("changed=null", "changed=mutate"), exchange(HttpRequest.GET("/mv/target/info").header(MUTATE, "true")));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/pre/info")));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/target/info").header(BODY, "untouched-mutate")));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/target/info").header(BODY, "clear")));
    }

    @Test
    void theViewHasTheSslConnectionOfTheRequest() {
        try (EmbeddedServer sslServer = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.certificate.self-signed.cert-a", "",
            "micronaut.certificate.self-signed.cert-b.subject", "CN=foo",
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.trust-name", "cert-b",
            "micronaut.server.ssl.client-authentication", "need",
            "micronaut.http.client.ssl.key-name", "cert-b",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient sslClient = sslServer.getApplicationContext().createBean(HttpClient.class, sslServer.getURI())) {

            String direct = sslClient.toBlocking().retrieve(HttpRequest.GET("/mv/target/info"));
            assertEquals(true, direct.contains(" secure=true ssl=true certificate=CN=foo "), direct);
            assertEquals(direct.replace("changed=null", "changed=mutate"), sslClient.toBlocking().retrieve(HttpRequest.GET("/mv/target/info").header(MUTATE, "true")));
            assertEquals(direct, sslClient.toBlocking().retrieve(HttpRequest.GET("/mv/pre/info")));
        }
    }

    @Test
    void theDirectRequestOfTheViewIsTheRequestUnlessTheBodyWasSet() {
        assertEquals("direct=true byteBody=true", exchange(HttpRequest.GET("/mv/target/direct")));
        assertEquals("direct=true byteBody=true", exchange(HttpRequest.GET("/mv/target/direct").header(MUTATE, "true")));
        assertEquals("direct=true byteBody=true", exchange(HttpRequest.GET("/mv/pre/direct")));
        assertEquals("direct=true byteBody=true", exchange(HttpRequest.GET("/mv/target/direct").header(BODY, "untouched-mutate")));
        for (String body : new String[]{"clear", "clear-mutate", "replace", "replace-mutate"}) {
            assertEquals("direct=false byteBody=false", exchange(HttpRequest.GET("/mv/target/direct").header(BODY, body)), body);
            assertEquals("direct=false byteBody=false", exchange(HttpRequest.GET("/mv/target/direct").header(LATE_BODY, body)), body);
        }
    }

    @Test
    void theBodyOfTheViewIsTheBodyTheFilterSet() {
        for (String header : new String[]{BODY, LATE_BODY}) {
            for (String path : new String[]{"/mv/target/body-required", "/mv/target/body-nullable"}) {
                assertEquals("body original", text(path, null, null), path);
                assertEquals("body original", text(path, header, "untouched-mutate"), path);
                assertEquals("body original", text(path, header, "unknown"), path);
                for (String body : new String[]{"replace", "replace-mutate"}) {
                    assertEquals("body replacement", text(path, header, body), header + " " + path + " " + body);
                }
            }
            for (String body : new String[]{"clear", "clear-mutate"}) {
                assertEquals("400", text("/mv/target/body-required", header, body), header + " " + body);
                assertEquals("body null", text("/mv/target/body-nullable", header, body), header + " " + body);
            }
        }
    }

    @Test
    void theBodyOfTheViewAfterBindingIsTheBodyOfTheRequest() {
        String direct = text("/mv/target/body-of-request", null, null);
        assertEquals("bound original request original", direct);
        assertEquals(direct, text("/mv/target/body-of-request", MUTATE, "true"));
        assertEquals(direct, text("/mv/pre/body-of-request", null, null));
        assertEquals(direct, text("/mv/target/body-of-request", BODY, "untouched-mutate"));
        assertEquals(direct, text("/mv/target/body-of-request", LATE_BODY, "untouched-mutate"));
        assertEquals("bound replacement request replacement", text("/mv/target/body-of-request", LATE_BODY, "replace-mutate"));
    }

    @Test
    void theBodyPartOfTheViewIsFromTheBodyTheFilterSet() {
        assertEquals("name Fred", json("/mv/target/json-field-nullable", null, null));
        assertEquals("name Fred", json("/mv/target/json-field-nullable", BODY, "untouched-mutate"));
        assertEquals("name Fred", json("/mv/target/json-field-nullable", MUTATE, "true"));
        assertEquals("name Replaced", json("/mv/target/json-field-nullable", BODY, "map"));
        assertEquals("name Replaced", json("/mv/target/json-field-nullable", LATE_BODY, "map"));
        assertEquals("name null", json("/mv/target/json-field-nullable", BODY, "clear"));
        assertEquals("name null", json("/mv/target/json-field-nullable", LATE_BODY, "clear-mutate"));
        assertEquals("map Replaced", json("/mv/target/json-map", BODY, "map"));
        assertEquals("map Replaced", json("/mv/target/json-map", LATE_BODY, "map-mutate"));
        assertEquals("map Fred", json("/mv/target/json-map", MUTATE, "true"));
    }

    @Test
    void theJsonBodyOfTheRequestIsBoundForTheView() {
        String expected = String.join("\n",
            "json Fred 42",
            "json field Fred 42",
            "json string {\"name\":\"Fred\",\"age\":42}",
            "json stream {\"name\":\"Fred\",\"age\":42}",
            "json future Fred 42",
            "json publisher Fred 42"
        );
        for (String[] variant : variants()) {
            String actual = String.join("\n",
                json(variant[0] + "/json-pojo", variant[1], variant[2]),
                json(variant[0] + "/json-field", variant[1], variant[2]),
                json(variant[0] + "/json-string", variant[1], variant[2]),
                json(variant[0] + "/json-stream", variant[1], variant[2]),
                json(variant[0] + "/json-future", variant[1], variant[2]),
                json(variant[0] + "/json-publisher", variant[1], variant[2])
            );
            assertEquals(expected, actual, String.join(" ", variant));
        }
    }

    @Test
    void theBodyTheFilterSetIsNotReadFromTheRequestByTheStreamingBinders() {
        for (String header : new String[]{BODY, LATE_BODY}) {
            for (String body : new String[]{"clear", "clear-mutate"}) {
                assertEquals("json stream null", json("/mv/target/json-stream-nullable", header, body), header + " " + body);
                assertEquals("json future null", json("/mv/target/json-future-nullable", header, body), header + " " + body);
                assertEquals("json publisher null", json("/mv/target/json-publisher-nullable", header, body), header + " " + body);
            }
        }
    }

    @Test
    void theUrlEncodedFormOfTheRequestIsBoundForTheView() {
        String expected = String.join("\n",
            "pojo Fred 42",
            "map Fred 42",
            "field Fred 42"
        );
        for (String[] variant : variants()) {
            String actual = String.join("\n",
                form(variant[0] + "/form-pojo", variant[1], variant[2]),
                form(variant[0] + "/form-map", variant[1], variant[2]),
                form(variant[0] + "/form-field", variant[1], variant[2])
            );
            assertEquals(expected, actual, String.join(" ", variant));
        }
        assertEquals("field Replaced 7", form("/mv/target/form-field", BODY, "map"));
        assertEquals("field Replaced 7", form("/mv/target/form-field", LATE_BODY, "map"));
    }

    @Test
    void theUploadsOfTheRequestAreBoundForTheView() {
        for (String[] variant : variants()) {
            String name = String.join(" ", variant);
            assertEquals("completed a.txt=first", multipart(variant[0] + "/completed", variant[1], variant[2], upload("first")), name);
            assertEquals("streaming a.txt=second", multipart(variant[0] + "/streaming", variant[1], variant[2], upload("second")), name);
            assertEquals("parts a.txt=third", multipart(variant[0] + "/parts", variant[1], variant[2], upload("third")), name);
            assertEquals("part Fred a.txt=fourth", multipart(variant[0] + "/part", variant[1], variant[2], uploadWithName("fourth")), name);
            assertEquals("multipart name,file", multipart(variant[0] + "/multipart", variant[1], variant[2], uploadWithName("fifth")), name);
        }
    }

    @Test
    void theUploadsOfTheRequestAreNotBoundWhenTheFilterSetTheBody() {
        for (String header : new String[]{BODY, LATE_BODY}) {
            for (String body : new String[]{"clear", "clear-mutate"}) {
                String name = header + " " + body;
                assertEquals("part null", multipart("/mv/target/part-nullable", header, body, uploadWithName("first")), name);
                assertEquals("multipart null", multipart("/mv/target/multipart-nullable", header, body, uploadWithName("second")), name);
                assertEquals("completed null", multipart("/mv/target/completed-nullable", header, body, uploadWithName("third")), name);
            }
        }
        assertEquals("part Fred", multipart("/mv/target/part-nullable", MUTATE, "true", uploadWithName("fourth")));
        assertEquals("multipart name,file", multipart("/mv/target/multipart-nullable", MUTATE, "true", uploadWithName("fifth")));
        assertEquals("completed a.txt", multipart("/mv/target/completed-nullable", MUTATE, "true", uploadWithName("sixth")));
    }

    @Test
    void theBodyOfTheViewIsNotAFormWhenTheBodyOfTheRequestIsNot() {
        for (String[] variant : variants()) {
            String name = String.join(" ", variant);
            assertEquals("part null", json(variant[0] + "/part-json", variant[1], variant[2]), name);
            assertEquals("multipart null", json(variant[0] + "/multipart-json", variant[1], variant[2]), name);
        }
    }

    @Test
    void aStreamedNettyBinderBindsTheRequestOfTheView() {
        // the binder binds the same for the view as for the request
        String direct = exchange(HttpRequest.GET("/mv/target/streamed"));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/target/streamed").header(MUTATE, "true")));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/pre/streamed")));
        assertEquals(direct, exchange(HttpRequest.GET("/mv/target/streamed").header(BODY, "untouched-mutate")));
        assertEquals("streamed none", exchange(HttpRequest.GET("/mv/target/streamed").header(BODY, "clear")));
        assertEquals("streamed none", exchange(HttpRequest.GET("/mv/target/streamed").header(LATE_BODY, "replace-mutate")));
    }

    @Test
    void theBodyRequestOfARequestThatIsNotANettyRequestIsNone() {
        assertNull(NettyHttpRequest.findBodyRequest(HttpRequest.GET("/mv/target/info")));
    }

    @Test
    void theBodyRequestOfTheViewIsTheRequest() {
        Map<String, String> found = server.getApplicationContext().getBean(MutatingFilters.class).found;
        found.clear();
        exchange(HttpRequest.POST("/mv/target/body-nullable", "original").contentType(MediaType.TEXT_PLAIN_TYPE).header(MUTATE, "true"));
        assertEquals("request=true view=true view-of-view=true body=true body-set=false body-set-body=null view-of-view-body=true", found.get("late"));
    }

    private static List<String[]> variants() {
        return List.of(
            new String[]{"/mv/target", "X-None", "none"},
            new String[]{"/mv/pre", "X-None", "none"},
            new String[]{"/mv/target", MUTATE, "true"},
            new String[]{"/mv/target", BODY, "untouched-mutate"},
            new String[]{"/mv/target", LATE_BODY, "untouched-mutate"},
            new String[]{"/mv/pre", MUTATE, "true"}
        );
    }

    private String text(String path, @Nullable String header, @Nullable String value) {
        return send(HttpRequest.POST(path, "original").contentType(MediaType.TEXT_PLAIN_TYPE), header, value);
    }

    private String json(String path, @Nullable String header, @Nullable String value) {
        return send(HttpRequest.POST(path, "{\"name\":\"Fred\",\"age\":42}").contentType(MediaType.APPLICATION_JSON_TYPE), header, value);
    }

    private String form(String path, @Nullable String header, @Nullable String value) {
        return send(HttpRequest.POST(path, "name=Fred&age=42").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), header, value);
    }

    private String multipart(String path, @Nullable String header, @Nullable String value, io.micronaut.http.client.multipart.MultipartBody body) {
        return send(HttpRequest.POST(path, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), header, value);
    }

    private String send(MutableHttpRequest<?> request, @Nullable String header, @Nullable String value) {
        if (header != null && value != null) {
            request.header(header, value);
        }
        try {
            return exchange(request);
        } catch (HttpClientResponseException e) {
            return String.valueOf(e.getStatus().getCode());
        }
    }

    private String exchange(HttpRequest<?> request) {
        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        assertEquals(200, response.code());
        return response.body();
    }

    private static io.micronaut.http.client.multipart.MultipartBody upload(String content) {
        return io.micronaut.http.client.multipart.MultipartBody.builder()
            .addPart("file", "a.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static io.micronaut.http.client.multipart.MultipartBody uploadWithName(String content) {
        return io.micronaut.http.client.multipart.MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("file", "a.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    /**
     * The connection of the request as the route sees it.
     */
    static String describe(HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        InetSocketAddress local = request.getServerAddress();
        return "remote=" + remote.getAddress().getHostAddress()
            + " server=" + local.getAddress().getHostAddress() + ":" + local.getPort()
            + " name=" + request.getServerName()
            + " version=" + request.getHttpVersion()
            + " secure=" + request.isSecure()
            + " ssl=" + request.getSslSession().isPresent()
            + " certificate=" + request.getCertificate().map(c -> ((X509Certificate) c).getSubjectX500Principal().getName()).orElse(null)
            + " changed=" + request.getParameters().get("changed");
    }

    static @Nullable HttpRequest<?> setBody(HttpRequest<?> request, @Nullable String body) {
        if (body == null) {
            return null;
        }
        return switch (body) {
            case "clear" -> request.mutate().body(null);
            case "clear-mutate" -> request.mutate().body(null).mutate();
            case "replace" -> request.mutate().body("replacement");
            case "replace-mutate" -> request.mutate().body("replacement").mutate();
            case "map" -> request.mutate().body(Map.of("name", "Replaced", "age", "7"));
            case "map-mutate" -> request.mutate().body(Map.of("name", "Replaced", "age", "7")).mutate();
            case "untouched-mutate" -> request.mutate().mutate();
            default -> null;
        };
    }

    @ServerFilter("/mv/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MutatingFilters {

        final Map<String, String> found = new ConcurrentHashMap<>();

        @RequestFilter
        @PreMatching
        @Nullable
        HttpRequest<?> preMatching(HttpRequest<?> request) {
            String path = request.getPath();
            if (path.startsWith("/mv/pre/")) {
                return request.mutate().uri(URI.create("/mv/target/" + path.substring("/mv/pre/".length())));
            }
            return setBody(request, request.getHeaders().get(BODY));
        }

        @RequestFilter
        @Nullable
        HttpRequest<?> afterMatch(HttpRequest<?> request) {
            if (request.getHeaders().contains(MUTATE)) {
                MutableHttpRequest<?> view = request.mutate();
                MutableHttpRequest<?> viewOfView = view.mutate();
                NettyHttpRequest<?> nettyRequest = NettyHttpRequest.findBodyRequest(request);
                found.put("late", "request=" + (nettyRequest == request)
                    + " view=" + (NettyHttpRequest.findBodyRequest(view) == nettyRequest)
                    + " view-of-view=" + (NettyHttpRequest.findBodyRequest(viewOfView) == nettyRequest)
                    + " body=" + viewOfView.getBody().equals(nettyRequest.getBody())
                    + " body-set=" + (NettyHttpRequest.findBodyRequest(view.body(null)) != null)
                    + " body-set-body=" + view.getBody().orElse(null)
                    + " view-of-view-body=" + viewOfView.getBody().equals(nettyRequest.getBody()));
                return request.mutate().uri(URI.create(request.getPath() + "?changed=mutate"));
            }
            return setBody(request, request.getHeaders().get(LATE_BODY));
        }
    }

    @Introspected
    record Person(String name, int age) {
    }

    /**
     * Bound by {@link StreamedInfoBinder}.
     *
     * @param nativeRequest The simple name of the class of the native request
     */
    record StreamedInfo(String nativeRequest) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class StreamedInfoBinder implements TypedRequestArgumentBinder<StreamedInfo>, StreamedNettyRequestArgumentBinder<StreamedInfo> {

        @Override
        public Argument<StreamedInfo> argumentType() {
            return Argument.of(StreamedInfo.class);
        }

        @Override
        public ArgumentBinder.BindingResult<StreamedInfo> bindForStreamedNettyRequest(ArgumentConversionContext<StreamedInfo> context,
                                                                                      StreamedHttpRequest streamedHttpRequest,
                                                                                      NettyHttpRequest<?> nettyHttpRequest) {
            StreamedInfo info = new StreamedInfo(streamedHttpRequest.getClass().getSimpleName());
            return () -> Optional.of(info);
        }
    }

    @Controller("/mv/target")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class TargetController {

        @Get("/info")
        String info(HttpRequest<?> request) {
            return describe(request);
        }

        @Get("/direct")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String direct(HttpRequest<?> request) {
            NettyHttpRequestBuilder builder = (NettyHttpRequestBuilder) request;
            boolean byteBody = builder.byteBodyDirect() != null;
            Optional<io.netty.handler.codec.http.HttpRequest> direct = builder.toHttpRequestDirect();
            direct.ifPresent(r -> {
                if (r instanceof StreamedHttpRequest streamed) {
                    Flux.from(streamed).doOnNext(ReferenceCountUtil::release).blockLast();
                }
                ReferenceCountUtil.release(r);
            });
            return "direct=" + direct.isPresent() + " byteBody=" + byteBody;
        }

        @Get("/streamed")
        String streamed(@Nullable StreamedInfo info) {
            return "streamed " + (info == null ? "none" : info.nativeRequest());
        }

        @Post(value = "/body-required", consumes = MediaType.TEXT_PLAIN)
        String bodyRequired(@Body String body) {
            return "body " + body;
        }

        @Post(value = "/body-nullable", consumes = MediaType.TEXT_PLAIN)
        String bodyNullable(@Nullable @Body String body) {
            return "body " + body;
        }

        @Post(value = "/body-of-request", consumes = MediaType.TEXT_PLAIN)
        String bodyOfRequest(@Body String body, HttpRequest<?> request) {
            return "bound " + body + " request " + request.getBody().orElse(null);
        }

        @Post(value = "/form-pojo", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formPojo(@Body Person person) {
            return "pojo " + person.name() + " " + person.age();
        }

        @Post(value = "/form-map", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formMap(@Body Map<String, String> form) {
            return "map " + form.get("name") + " " + form.get("age");
        }

        @Post(value = "/form-field", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formField(@Body("name") String name, @Body("age") int age) {
            return "field " + name + " " + age;
        }

        @Post(value = "/json-pojo", consumes = MediaType.APPLICATION_JSON)
        String jsonPojo(@Body Person person) {
            return "json " + person.name() + " " + person.age();
        }

        @Post(value = "/json-field", consumes = MediaType.APPLICATION_JSON)
        String jsonField(@Body("name") String name, @Body("age") int age) {
            return "json field " + name + " " + age;
        }

        @Post(value = "/json-field-nullable", consumes = MediaType.APPLICATION_JSON)
        String jsonFieldNullable(@Nullable @Body("name") String name) {
            return "name " + name;
        }

        @Post(value = "/json-map", consumes = MediaType.APPLICATION_JSON)
        String jsonMap(@Body Map<String, Object> body) {
            return "map " + body.get("name");
        }

        @Post(value = "/json-string", consumes = MediaType.APPLICATION_JSON)
        String jsonString(@Body String body) {
            return "json string " + body;
        }

        @Post(value = "/json-stream", consumes = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String jsonStream(@Body InputStream body) throws IOException {
            try (body) {
                return "json stream " + new String(body.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/json-stream-nullable", consumes = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String jsonStreamNullable(@Nullable @Body InputStream body) throws IOException {
            if (body == null) {
                return "json stream null";
            }
            try (body) {
                return "json stream " + new String(body.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/json-future", consumes = MediaType.APPLICATION_JSON)
        CompletableFuture<String> jsonFuture(@Body CompletableFuture<Person> person) {
            return person.thenApply(p -> "json future " + p.name() + " " + p.age());
        }

        @Post(value = "/json-future-nullable", consumes = MediaType.APPLICATION_JSON)
        CompletableFuture<String> jsonFutureNullable(@Nullable @Body CompletableFuture<Person> person) {
            if (person == null) {
                return CompletableFuture.completedFuture("json future null");
            }
            return person.thenApply(p -> "json future " + (p == null ? null : p.name()));
        }

        @Post(value = "/json-publisher", consumes = MediaType.APPLICATION_JSON)
        Mono<String> jsonPublisher(@Body Mono<Person> person) {
            return person.map(p -> "json publisher " + p.name() + " " + p.age());
        }

        @Post(value = "/json-publisher-nullable", consumes = MediaType.APPLICATION_JSON)
        Mono<String> jsonPublisherNullable(@Nullable @Body Mono<Person> person) {
            if (person == null) {
                return Mono.just("json publisher null");
            }
            return person.map(p -> "json publisher " + p.name()).defaultIfEmpty("json publisher null");
        }

        @Post(value = "/part", consumes = MediaType.MULTIPART_FORM_DATA)
        String part(@Part("name") String name, CompletedFileUpload file) throws IOException {
            return "part " + name + " " + file.getFilename() + "=" + new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        @Post(value = "/part-nullable", consumes = MediaType.MULTIPART_FORM_DATA)
        String partNullable(@Nullable @Part("name") String name) {
            return "part " + name;
        }

        @Post(value = "/part-json", consumes = MediaType.APPLICATION_JSON)
        String partJson(@Nullable @Part("missing") String name) {
            return "part " + name;
        }

        @Post(value = "/multipart-json", consumes = MediaType.APPLICATION_JSON)
        String multipartJson(@Nullable @Body MultipartBody body) {
            return "multipart " + (body == null ? null : "present");
        }

        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA)
        String completed(CompletedFileUpload file) throws IOException {
            return "completed " + file.getFilename() + "=" + new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        @Post(value = "/completed-nullable", consumes = MediaType.MULTIPART_FORM_DATA)
        String completedNullable(@Nullable CompletedFileUpload file) {
            return "completed " + (file == null ? null : file.getFilename());
        }

        @Post(value = "/streaming", consumes = MediaType.MULTIPART_FORM_DATA)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String streaming(StreamingFileUpload file) throws IOException {
            try (InputStream stream = file.asInputStream()) {
                return "streaming " + file.getFilename() + "=" + new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/parts", consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<String> parts(Publisher<CompletedFileUpload> file) {
            return Flux.from(file).map(upload -> {
                try {
                    return "parts " + upload.getFilename() + "=" + new String(upload.getBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        @Post(value = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<String> multipart(@Body MultipartBody body) {
            return Flux.from(body).map(CompletedPart::getName).collectList().map(names -> "multipart " + String.join(",", names));
        }

        @Post(value = "/multipart-nullable", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<String> multipartNullable(@Nullable @Body MultipartBody body) {
            if (body == null) {
                return Mono.just("multipart null");
            }
            return Flux.from(body).map(CompletedPart::getName).collectList().map(names -> "multipart " + String.join(",", names));
        }
    }
}
