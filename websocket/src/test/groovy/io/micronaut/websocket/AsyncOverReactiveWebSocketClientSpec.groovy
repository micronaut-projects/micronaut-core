package io.micronaut.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.ExecutionException

class AsyncOverReactiveWebSocketClientSpec extends Specification {
    void 'an empty publisher fails the connect'() {
        given:
        def client = new ControlledClient()
        def future = client.toAsyncWebSocket().connect(Endpoint, HttpRequest.GET('/')).toCompletableFuture()

        when:
        client.subscriber.onComplete()
        future.get()

        then:
        ExecutionException failure = thrown()
        failure.cause instanceof NoSuchElementException
    }

    void 'cancelling before the subscription arrives cancels that subscription'() {
        given:
        def client = new ControlledClient()
        def future = client.toAsyncWebSocket().connect(Endpoint, [:]).toCompletableFuture()

        when:
        future.cancel(false)
        client.subscribe()

        then:
        client.cancelled == 1
        client.requested == 0
    }

    void 'external completion closes late endpoints even when their close fails'(boolean closeFails) {
        given:
        def client = new ControlledClient()
        def future = client.toAsyncWebSocket().connect(Endpoint, [:]).toCompletableFuture()
        client.subscribe()
        def endpoint = new Endpoint(closeFails: closeFails)

        when:
        future.completeExceptionally(new IllegalStateException('abandoned'))
        client.subscriber.onNext(endpoint)
        client.subscriber.onComplete()

        then:
        client.cancelled == 1
        endpoint.closed
        future.isCompletedExceptionally()

        where:
        closeFails << [false, true]
    }

    void 'the first endpoint wins and another endpoint is closed'() {
        given:
        def client = new ControlledClient()
        def future = client.toAsyncWebSocket().connect(Endpoint, [:]).toCompletableFuture()
        client.subscribe()
        def first = new Endpoint()
        def late = new Endpoint()

        when:
        client.subscriber.onNext(first)
        client.subscriber.onNext(late)
        client.subscriber.onComplete()

        then:
        future.get().is(first)
        client.cancelled == 1
        !first.closed
        late.closed

        cleanup:
        first.close()
    }

    void 'publisher errors are preserved and closing the adapter closes its client'() {
        given:
        def client = new ControlledClient()
        def async = client.toAsyncWebSocket()
        def future = async.connect(Endpoint, [:]).toCompletableFuture()
        def error = new IllegalStateException('failed')

        when:
        client.subscriber.onError(error)
        future.get()

        then:
        ExecutionException failure = thrown()
        failure.cause.is(error)

        when:
        async.close()

        then:
        client.closed
    }

    private static class Endpoint implements AutoCloseable {
        boolean closed
        boolean closeFails

        @Override
        void close() {
            closed = true
            if (closeFails) {
                throw new IllegalStateException('close failed')
            }
        }
    }

    private static class ControlledClient implements WebSocketClient {
        Subscriber subscriber
        int requested
        int cancelled
        boolean closed

        @Override
        <T extends AutoCloseable> Publisher<T> connect(Class<T> type, MutableHttpRequest<?> request) {
            return { Subscriber<T> target -> subscriber = target } as Publisher<T>
        }

        @Override
        <T extends AutoCloseable> Publisher<T> connect(Class<T> type, Map<String, Object> parameters) {
            return { Subscriber<T> target -> subscriber = target } as Publisher<T>
        }

        void subscribe() {
            subscriber.onSubscribe(new Subscription() {
                @Override
                void request(long count) {
                    requested += count
                }

                @Override
                void cancel() {
                    cancelled++
                }
            })
        }

        @Override
        void close() {
            closed = true
        }
    }
}
