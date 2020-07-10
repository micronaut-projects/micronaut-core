package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@MicronautTest
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
    RxHttpClient client;

    @Inject
    ServerSslBuilder serverSslBuilder;

    @Test
    void testPost() {
        HttpResponse<String> result = client.exchange(HttpRequest.POST("/vertx/demo/testPost", "Request-1")
                .contentType(MediaType.TEXT_PLAIN), String.class)
                .blockingFirst();

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-1",
                result.body()
        );

        result = client.exchange(HttpRequest.POST("/vertx/demo/testPost", "Request-2")
                .contentType(MediaType.TEXT_PLAIN), String.class)
                .blockingFirst();

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
        HttpClient client = vertx.createHttpClient(options);
        CompletableFuture<String> result = new CompletableFuture<>();
        // Going to send 2 POST requests. 2nd request will not be succeessful
        client.request(HttpMethod.POST, "/vertx/demo/testPost", response -> {
            System.out.println("Received response with status code " + response.statusCode() + " " + response.version());
            response.bodyHandler(buffer -> result.complete(new String(buffer.getBytes())));
        })
                .putHeader("content-length", "1000")
                .write("Request-1")
                .end();

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-1",
                result.get()
        );

        CompletableFuture<String> result2 = new CompletableFuture<>();
        client.request(HttpMethod.POST, "/vertx/demo/testPost", response -> {
            System.out.println("Received response with status code " + response.statusCode() + " " + response.version());
            response.bodyHandler(buffer -> result2.complete(new String(buffer.getBytes())));
        })
        .putHeader("content-length", "1000")
        .write("Request-2")
        .end();

        Assertions.assertEquals(
                "Test succeeded on POST. Received : Request-2",
                result2.get(2, TimeUnit.SECONDS)
        );
    }

    @Nonnull
    @Override
    public Map<String, String> getProperties() {
        return CollectionUtils.mapOf(
                "micronaut.ssl.enabled", true,
                "micronaut.ssl.buildSelfSigned", true
        );
    }

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
