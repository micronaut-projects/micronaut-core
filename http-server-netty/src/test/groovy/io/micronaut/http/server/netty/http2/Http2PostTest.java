package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@MicronautTest
@Property(name = "spec.name", value = "Http2PostTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.netty.log-level", value = "TRACE")
@Property(name = "micronaut.http.client.log-level", value = "TRACE")
@Requires(sdk = Requires.Sdk.JAVA, version = "11")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Http2PostTest implements TestPropertyProvider {

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    @Client(value = "/", httpVersion = HttpVersion.HTTP_2_0)
    HttpClient client;

    @Inject
    ServerSslBuilder serverSslBuilder;

    @Test
    void testPost() {
        HttpResponse<String> result = Flux.from(client.exchange(HttpRequest.POST("/vertx/demo/testPost", "Request-1")
                .contentType(MediaType.TEXT_PLAIN), String.class))
                .blockFirst();

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-1",
                result.body()
        );

        result = Flux.from(client.exchange(HttpRequest.POST("/vertx/demo/testPost", "Request-2")
                .contentType(MediaType.TEXT_PLAIN), String.class))
                .blockFirst();

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-2",
                result.body()
        );
    }


    @Test
    void testPostVertx() throws ExecutionException, InterruptedException, TimeoutException {
        Vertx vertx = Vertx.vertx();
        HttpClientOptions options = new HttpClientOptions()
                .setProtocolVersion(io.vertx.core.http.HttpVersion.HTTP_2)
                .setSsl(true)
//                .setLogActivity(true)
                .setTrustAll(true).setVerifyHost(false)
                .setUseAlpn(true)
                .setDefaultHost("localhost")
                .setDefaultPort(embeddedServer.getPort());
        io.vertx.core.http.HttpClient client = vertx.createHttpClient(options);
        // Going to send 2 POST requests. 2nd request will not be succeessful
        HttpClientResponse response1 = client.request(HttpMethod.POST, "/vertx/demo/testPost")
            .toCompletionStage().toCompletableFuture().get()
            .putHeader("content-length", "9")
            .send("Request-1")
            .onSuccess(resp -> {
                // trigger loading body
                resp.body();
            })
            .toCompletionStage().toCompletableFuture().get();
        System.out.println("Received response with status code " + response1.statusCode() + " " + response1.version());

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-1",
            response1.body().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8)
        );

        HttpClientResponse response2 = client.request(HttpMethod.POST, "/vertx/demo/testPost")
            .toCompletionStage().toCompletableFuture().get()
            .putHeader("content-length", "9")
            .send("Request-2")
            .onSuccess(resp -> {
                // trigger loading body
                resp.body();
            })
            .toCompletionStage().toCompletableFuture().get();
        System.out.println("Received response with status code " + response2.statusCode() + " " + response2.version());

        Assertions.assertEquals(
            "Test succeeded on POST. Received : Request-2",
            response2.body().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8)
        );
    }

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        return CollectionUtils.mapOf(
                "micronaut.ssl.enabled", true,
                "micronaut.server.ssl.buildSelfSigned", true,
                "micronaut.server.ssl.port", -1,
                "micronaut.http.client.ssl.insecure-trust-all-certificates", true
        );
    }

    @Requires(property = "spec.name", value = "Http2PostTest")
    @Controller("/vertx/demo")
    public static class DemoController {
        @Get("/testGet")
        public HttpResponse<String> testGet() {
            return HttpResponse.ok("Test succeeded on GET");
        }

        @Post(value = "/testPost", produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        public HttpResponse<String> testPost(@Body String body) {
            return HttpResponse
                    .ok("Test succeeded on POST. Received : " + body)
                    .contentType(MediaType.TEXT_PLAIN);
        }

    }

}
