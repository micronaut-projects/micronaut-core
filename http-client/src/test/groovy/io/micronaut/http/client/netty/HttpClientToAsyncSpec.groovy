package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.AsyncHttpClient
import io.micronaut.http.client.DefaultAsyncOverReactiveHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class HttpClientToAsyncSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpClientToAsyncSpec'
    ])

    void "http client toAsync adapts to CompletionStage API"() {
        given:
            HttpClient httpClient = HttpClient.create(server.URL)
        def asyncClient = httpClient.toAsync()

        expect:
        asyncClient.retrieve(HttpRequest.GET("/async/hello"), String)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS) == "hello"

        cleanup:
        asyncClient.close()
    }

    @Unroll
    void "async http client default methods behave correctly (#clientVariant)"(String clientVariant) {
        given:
        def holder = createAsyncClient(clientVariant)
            AsyncHttpClient asyncClient = holder.async
        asyncClient.start()
        assert asyncClient.isRunning()

        when: "exchange with explicit body and error types"
        def responseWithExplicitTypes = asyncClient.exchange(HttpRequest.GET("/async/hello"), Argument.of(String), HttpClient.DEFAULT_ERROR_TYPE)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        responseWithExplicitTypes.status == HttpStatus.OK
        responseWithExplicitTypes.body.orElse(null) == "hello"

        when: "exchange with default error type"
        def responseWithDefaultError = asyncClient.exchange(HttpRequest.GET("/async/hello"), Argument.of(String))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        responseWithDefaultError.body.orElse(null) == "hello"

        when: "exchange returning ByteBuffer using request overload"
        def byteBufferResponse = asyncClient.exchange(HttpRequest.GET("/async/bytes"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        toUtf8(byteBufferResponse.body.orElseThrow()) == "bytes"

        when: "exchange returning ByteBuffer using URI overload"
        def byteBufferUriResponse = asyncClient.exchange("/async/bytes")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        toUtf8(byteBufferUriResponse.body.orElseThrow()) == "bytes"

        when: "exchange with URI and body class"
        def responseWithUriAndClass = asyncClient.exchange("/async/hello", String)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        responseWithUriAndClass.body.orElse(null) == "hello"

        when: "exchange with request and body class"
        def responseWithRequestAndClass = asyncClient.exchange(HttpRequest.GET("/async/hello"), String)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        responseWithRequestAndClass.body.orElse(null) == "hello"

        when: "retrieve with explicit argument and error"
        def retrievedExplicit = asyncClient.retrieve(HttpRequest.GET("/async/hello"), Argument.of(String), HttpClient.DEFAULT_ERROR_TYPE)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedExplicit == "hello"

        when: "retrieve with explicit argument"
        def retrievedArgument = asyncClient.retrieve(HttpRequest.GET("/async/hello"), Argument.of(String))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedArgument == "hello"

        when: "retrieve with body class"
        def retrievedClass = asyncClient.retrieve(HttpRequest.GET("/async/hello"), String)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedClass == "hello"

        when: "retrieve with request defaulting to String"
        def retrievedDefault = asyncClient.retrieve(HttpRequest.GET("/async/hello"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedDefault == "hello"

        when: "retrieve with URI defaulting to String"
        def retrievedUri = asyncClient.retrieve("/async/hello")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedUri == "hello"

        when: "retrieve HttpStatus using argument overload"
        def retrievedStatus = asyncClient.retrieve(HttpRequest.GET("/async/status"), Argument.of(HttpStatus))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedStatus == HttpStatus.ACCEPTED

        when: "retrieve void with explicit argument overload"
        def retrievedVoid = asyncClient.retrieve(HttpRequest.GET("/async/empty"), Argument.VOID)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        retrievedVoid == null

        when: "exchange void with explicit argument overload"
        def exchangedVoid = asyncClient.exchange(HttpRequest.GET("/async/empty"), Argument.VOID)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
        then:
        exchangedVoid.status == HttpStatus.NO_CONTENT
        !exchangedVoid.body.present

        when: "stopping the client"
        asyncClient.stop()
        then:
        !asyncClient.isRunning()

        cleanup:
        holder.cleanup.call()
        where:
        clientVariant << ["default-over-reactive", "netty"]
    }

    @Controller("/async")
    @Requires(property = 'spec.name', value = 'HttpClientToAsyncSpec')
    static class AsyncController {

        @Get("/hello")
        String hello() {
            return "hello"
        }

        @Get("/bytes")
        byte[] bytes() {
            return "bytes".getBytes(StandardCharsets.UTF_8)
        }

        @Get("/status")
        HttpResponse<?> status() {
            return HttpResponse.accepted()
        }

        @Get("/empty")
        HttpResponse<?> empty() {
            return HttpResponse.noContent()
        }
    }

    private Map createAsyncClient(String variant) {
        switch (variant) {
            case "default-over-reactive":
                HttpClient reactiveClient = HttpClient.create(server.URL)
                def asyncReactive = new DefaultAsyncOverReactiveHttpClient(reactiveClient)
                return [async: asyncReactive, cleanup: { -> asyncReactive.close() }]
            case "netty":
                DefaultHttpClient defaultHttpClient = DefaultHttpClient.builder()
                        .uri(server.URI)
                        .build()
                def asyncNetty = new DefaultAsyncHttpClient(defaultHttpClient.getNettyHttpClient())
                return [async: asyncNetty, cleanup: { ->
                    try {
                        asyncNetty.close()
                    } finally {
                        defaultHttpClient.close()
                    }
                }]
            default:
                throw new IllegalArgumentException("Unknown variant: $variant")
        }
    }

    private static String toUtf8(ByteBuffer<?> buffer) {
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8)
    }
}
