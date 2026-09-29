package io.micronaut.http.client

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpVersion
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.exceptions.HttpClientException
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class AsyncRawHttpClientSpec extends Specification {

    void setup() {
        StubRawHttpClientFactory.CREATED.clear()
    }

    void "create resolves the raw client factory from the service loader and adapts its client"() {
        when:
        AsyncRawHttpClient client = AsyncRawHttpClient.create(URI.create("http://upstream"))

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        StubRawHttpClientFactory.CREATED.size() == 1
        StubRawHttpClientFactory.CREATED[0].url == URI.create("http://upstream")
        StubRawHttpClientFactory.CREATED[0].configuration instanceof DefaultHttpClientConfiguration
        !StubRawHttpClientFactory.CREATED[0].client.closed

        when: 'closing the async client closes the raw client'
        client.close()

        then:
        StubRawHttpClientFactory.CREATED[0].client.closed
    }

    void "create with a configuration passes the configuration to the factory"() {
        given:
        HttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.readTimeout = Duration.ofSeconds(42)

        when:
        AsyncRawHttpClient client = AsyncRawHttpClient.create(URI.create("http://upstream"), configuration)

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        StubRawHttpClientFactory.CREATED.size() == 1
        StubRawHttpClientFactory.CREATED[0].url == URI.create("http://upstream")
        StubRawHttpClientFactory.CREATED[0].configuration.is(configuration)

        cleanup:
        client?.close()
    }

    void "create accepts a client without a base URL"() {
        when:
        AsyncRawHttpClient client = AsyncRawHttpClient.create(null)

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        StubRawHttpClientFactory.CREATED.size() == 1
        StubRawHttpClientFactory.CREATED[0].url == null

        cleanup:
        client?.close()
    }

    void "the raw client is created through the same factory"() {
        given:
        HttpClientConfiguration configuration = new DefaultHttpClientConfiguration()

        when:
        RawHttpClient created = RawHttpClient.create(URI.create("http://upstream"))
        RawHttpClient configured = RawHttpClient.create(URI.create("http://other"), configuration)

        then:
        created.is(StubRawHttpClientFactory.CREATED[0].client)
        configured.is(StubRawHttpClientFactory.CREATED[1].client)
        StubRawHttpClientFactory.CREATED[1].url == URI.create("http://other")
        StubRawHttpClientFactory.CREATED[1].configuration.is(configuration)

        cleanup:
        created?.close()
        configured?.close()
    }

    void "the factory creates an async client from its raw client by default"() {
        given:
        RawHttpClientFactory factory = new StubRawHttpClientFactory()
        HttpClientConfiguration configuration = new DefaultHttpClientConfiguration()

        when:
        AsyncRawHttpClient client = factory.createAsyncRawClient(URI.create("http://upstream"), configuration)

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        StubRawHttpClientFactory.CREATED[0].configuration.is(configuration)

        when:
        client.close()

        then:
        StubRawHttpClientFactory.CREATED[0].client.closed
    }

    void "the registry adapts its raw client by default"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        HttpResponse<?> response = HttpResponse.ok()
        raw.response = Mono.just(response)
        List<Object> lookups = []
        RawHttpClientRegistry registry = new RawHttpClientRegistry() {
            @Override
            RawHttpClient getRawClient(HttpVersionSelection httpVersion, String clientId, String path) {
                lookups.addAll([httpVersion, clientId, path])
                return raw
            }
        }
        HttpVersionSelection version = HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_1_1)

        when:
        AsyncRawHttpClient client = registry.getAsyncRawClient(version, "upstream", "/base")

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        lookups == [version, "upstream", "/base"]
        client.exchange(HttpRequest.GET("/path"), null).toCompletableFuture().get(10, TimeUnit.SECONDS).is(response)
    }

    void "the adapter sends the exchange without a blocked thread and completes with its response"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        Map response = response("hello")
        raw.response = Mono.just(response.response)
        Map requestBody = body("request")
        HttpRequest<?> request = HttpRequest.POST("http://upstream/echo", null)
        AsyncRawHttpClient client = raw.toAsyncRaw()

        when:
        HttpResponse<?> received = client.exchange(request, requestBody.body).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        client instanceof DefaultAsyncOverRawHttpClient
        received.is(response.response)
        raw.exchanges.size() == 1
        raw.exchanges[0][0].is(request)
        raw.exchanges[0][1].is(requestBody.body)
        raw.exchanges[0][2] == null
        !response.closed()
        ((ByteBodyHttpResponse<?>) received).byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "hello"

        cleanup:
        ((ByteBodyHttpResponse<?>) response.response).close()
        requestBody.body.close()
    }

    void "the adapter passes a missing request body on"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        raw.response = Mono.just(HttpResponse.noContent())

        when:
        HttpResponse<?> received = new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        received.code() == 204
        raw.exchanges.size() == 1
        raw.exchanges[0][1] == null
    }

    void "the adapter passes the options of an exchange on"() {
        given:
        StubOptionsRawHttpClient raw = new StubOptionsRawHttpClient()
        raw.response = Mono.just(HttpResponse.ok())
        RawRequestOptions options = RawRequestOptions.proxy().toBuilder().responseTimeout(Duration.ofSeconds(3)).build()

        when:
        HttpResponse<?> received = new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null, options)
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        received.code() == 200
        raw.options == [options]
        raw.exchanges.size() == 1
        raw.exchanges[0][2] == null
    }

    void "the default options of a raw client without option support send a plain exchange"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        raw.response = Mono.just(HttpResponse.ok())
        Map requestBody = body("request")

        when:
        HttpResponse<?> received = raw.toAsyncRaw().exchange(HttpRequest.POST("http://upstream", null), requestBody.body, RawRequestOptions.getDefault())
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        received.code() == 200
        raw.exchanges.size() == 1
        raw.exchanges[0][1].is(requestBody.body)
        !requestBody.closed()

        cleanup:
        requestBody.body.close()
    }

    void "other options of a raw client without option support fail fast and release the request body"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        Map requestBody = body("request")

        when:
        raw.toAsyncRaw().exchange(HttpRequest.POST("http://upstream", null), requestBody.body, RawRequestOptions.proxy())

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains(StubRawHttpClient.name)
        requestBody.closed()
        raw.exchanges.isEmpty()

        when: 'without a request body'
        raw.toAsyncRaw().exchange(HttpRequest.GET("http://upstream"), null, RawRequestOptions.proxy())

        then:
        thrown(UnsupportedOperationException)
    }

    void "the adapter fails with the error of the exchange"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        HttpClientException error = new HttpClientException("Connection refused")
        raw.response = Mono.error(error)

        when:
        new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void "the adapter fails when the exchange completes without a response"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        raw.response = Mono.empty()

        when:
        new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof IllegalStateException
    }

    void "the adapter completes asynchronously when the response arrives later"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()
        Map response = response("late")
        raw.response = Mono.delay(Duration.ofMillis(50)).thenReturn(response.response)

        when:
        CompletableFuture<HttpResponse<?>> future = new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null).toCompletableFuture()

        then:
        future.get(10, TimeUnit.SECONDS).is(response.response)

        cleanup:
        ((ByteBodyHttpResponse<?>) response.response).close()
    }

    void "cancelling before the subscription arrives cancels the subscription once it does"() {
        given:
        Subscriber<? super HttpResponse<?>> subscriber = null
        boolean cancelled = false
        boolean requested = false
        Publisher<HttpResponse<?>> publisher = { Subscriber<? super HttpResponse<?>> s -> subscriber = s } as Publisher<HttpResponse<?>>
        StubRawHttpClient raw = new StubRawHttpClient()
        raw.response = publisher

        when:
        CompletableFuture<HttpResponse<?>> future = new DefaultAsyncOverRawHttpClient(raw).exchange(HttpRequest.GET("http://upstream"), null).toCompletableFuture()

        then:
        future.cancel(false)

        when:
        subscriber.onSubscribe(new Subscription() {
            @Override
            void request(long n) {
                requested = true
            }

            @Override
            void cancel() {
                cancelled = true
            }
        })

        then:
        cancelled
        !requested
        future.isCancelled()
    }

    void "closing the adapter closes the raw client"() {
        given:
        StubRawHttpClient raw = new StubRawHttpClient()

        when:
        new DefaultAsyncOverRawHttpClient(raw).close()

        then:
        raw.closed
    }

    void "the adapter requires a raw client"() {
        when:
        new DefaultAsyncOverRawHttpClient(null)

        then:
        thrown(NullPointerException)
    }

    private static Map body(String content) {
        boolean closed = false
        CloseableByteBody delegate = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(content.bytes)
        CloseableByteBody tracked = new CloseableByteBody() {
            @Delegate(excludes = ['close'])
            CloseableByteBody wrapped = delegate

            @Override
            void close() {
                closed = true
                wrapped.close()
            }
        }
        return [body: tracked, closed: { -> closed }]
    }

    private static Map response(String content) {
        Map body = body(content)
        ByteBodyHttpResponse<?> response = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), body.body)
        return [response: response, closed: body.closed]
    }
}
