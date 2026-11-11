package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.body.CloseableByteBody
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * The {@link RawHttpClientFactory} that {@link RawHttpClientFactoryResolver} loads in the tests
 * of this module (registered in {@code META-INF/services}): it records the clients it creates.
 */
class StubRawHttpClientFactory implements RawHttpClientFactory {
    static final List<Created> CREATED = Collections.synchronizedList(new ArrayList<Created>())

    @Override
    RawHttpClient createRawClient(@Nullable URI url, HttpClientConfiguration configuration) {
        StubRawHttpClient client = new StubRawHttpClient()
        CREATED.add(new Created(url, configuration, client))
        return client
    }

    static class Created {
        final @Nullable URI url
        final HttpClientConfiguration configuration
        final StubRawHttpClient client

        Created(@Nullable URI url, HttpClientConfiguration configuration, StubRawHttpClient client) {
            this.url = url
            this.configuration = configuration
            this.client = client
        }
    }
}

/**
 * A {@link RawHttpClient} that answers every exchange with {@link #response} and records the
 * exchanges. It only implements the abstract methods, so the default methods of the interface
 * apply.
 */
class StubRawHttpClient implements RawHttpClient {
    Publisher<? extends HttpResponse<?>> response = Mono.empty()
    final List<List<Object>> exchanges = Collections.synchronizedList(new ArrayList<List<Object>>())
    volatile boolean closed

    @Override
    Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        exchanges.add([request, requestBody, blockedThread])
        return response
    }

    @Override
    void close() {
        closed = true
    }
}

/**
 * A {@link StubRawHttpClient} that supports per-exchange options.
 */
class StubOptionsRawHttpClient extends StubRawHttpClient {
    final List<RawRequestOptions> options = Collections.synchronizedList(new ArrayList<RawRequestOptions>())

    @Override
    Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, RawRequestOptions options) {
        this.options.add(options)
        return exchange(request, requestBody, blockedThread)
    }
}
