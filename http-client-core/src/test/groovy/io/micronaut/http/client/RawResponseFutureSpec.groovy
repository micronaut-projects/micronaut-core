package io.micronaut.http.client

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class RawResponseFutureSpec extends Specification {

    void "cancelling the future of a flow cancels the flow, closes the request body and a late response"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> flow = DelayedExecutionFlow.create()
        def requestBody = body("request")
        def late = response()
        RawResponseFuture future = RawResponseFuture.of(flow, requestBody.body)

        when:
        boolean cancelled = future.cancel(false)

        then:
        cancelled
        flow.isCancelled()
        requestBody.closed()
        !late.closed()

        when:
        flow.complete(late.response)

        then:
        late.closed()
        future.isCancelled()
    }

    void "the future of a flow completes with the response and closes the request body"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> flow = DelayedExecutionFlow.create()
        def requestBody = body("request")
        def response = response()
        RawResponseFuture future = RawResponseFuture.of(flow, requestBody.body)

        when:
        flow.complete(response.response)

        then:
        future.join().is(response.response)
        requestBody.closed()
        !response.closed()
        !future.cancel(false)
        !response.closed()
    }

    void "the future of a flow fails with its error"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> flow = DelayedExecutionFlow.create()
        def requestBody = body("request")
        RawResponseFuture future = RawResponseFuture.of(flow, requestBody.body)

        when:
        flow.completeExceptionally(new IOException("boom"))
        future.join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof IOException
        requestBody.closed()
    }

    void "cancelling the future of a publisher cancels the subscription and closes a late response"() {
        given:
        Sinks.One<HttpResponse<?>> sink = Sinks.one()
        boolean subscriptionCancelled = false
        def late = response()
        RawResponseFuture future = RawResponseFuture.of(sink.asMono().doOnCancel { subscriptionCancelled = true })

        when:
        future.cancel(false)

        then:
        subscriptionCancelled
        future.isCancelled()

        when: 'the response was already on its way'
        RawResponseFuture racing = RawResponseFuture.of(Mono.just(late.response))

        then: 'a completed future is not cancelled'
        !racing.cancel(false)
        !late.closed()
    }

    void "the future of an empty publisher fails"() {
        when:
        RawResponseFuture.of(Mono.empty()).join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof IllegalStateException
    }

    void "cancelling a derived stage does not cancel the exchange"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> flow = DelayedExecutionFlow.create()
        RawResponseFuture future = RawResponseFuture.of(flow, body("request").body)
        CompletableFuture<Integer> derived = future.thenApply { it.code() }

        when:
        derived.cancel(false)

        then:
        !flow.isCancelled()
        !future.isDone()

        when:
        flow.complete(response().response)

        then:
        future.join().code() == 200

        when:
        derived.join()

        then:
        thrown(CancellationException)
    }

    void "the adapter over a raw client cancels the subscription of the exchange"() {
        given:
        Sinks.One<HttpResponse<?>> sink = Sinks.one()
        boolean subscriptionCancelled = false
        RawHttpClient rawClient = Mock(RawHttpClient)
        rawClient.exchange(_, _, _) >> sink.asMono().doOnCancel { subscriptionCancelled = true }
        rawClient.toAsyncRaw() >> { new DefaultAsyncOverRawHttpClient(rawClient) }

        when:
        CompletableFuture<HttpResponse<?>> future = rawClient.toAsyncRaw().exchange(HttpRequest.GET("http://localhost"), null).toCompletableFuture()
        future.cancel(false)

        then:
        subscriptionCancelled
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

    private static Map response() {
        def body = body("late")
        ByteBodyHttpResponse<?> response = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), body.body)
        return [response: response, closed: body.closed]
    }
}
