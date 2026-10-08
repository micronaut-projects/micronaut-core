package io.micronaut.http.client

import io.micronaut.http.client.internal.ElementsStages
import io.micronaut.http.client.internal.ElementsResponse

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.body.BodyElements
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.function.Function

class ElementsStagesSpec extends Specification {

    void "the elements are closed when the mapping of the response fails"() {
        given:
        def elements = new RecordingElements()
        def source = new CompletableFuture<HttpResponse<BodyElements<String>>>()
        def mapped = ElementsStages.mapResponse(source, { r -> throw new IllegalStateException("mapping failed") } as Function)

        when:
        source.complete(ElementsResponse.of(HttpResponse.ok(), elements))

        then:
        elements.closed
        mapped.toCompletableFuture().isCompletedExceptionally()

        when:
        mapped.toCompletableFuture().join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof IllegalStateException
    }

    void "the elements are closed when the mapping of the elements fails"() {
        given:
        def elements = new RecordingElements()
        def source = new CompletableFuture<BodyElements<String>>()
        def mapped = ElementsStages.mapElements(source, { r -> throw new IllegalStateException("mapping failed") } as Function)

        when:
        source.complete(elements)

        then:
        elements.closed
        mapped.toCompletableFuture().isCompletedExceptionally()
    }

    void "cancelling the mapped stage cancels the source"() {
        given:
        def source = new CompletableFuture<BodyElements<String>>()
        def mapped = ElementsStages.mapElements(source, Function.identity())

        when:
        mapped.toCompletableFuture().cancel(false)

        then:
        source.isCancelled()
    }

    void "a result that arrives after the mapped stage was cancelled is closed"() {
        given:
        def elements = new RecordingElements()
        def stubborn = new CompletableFuture<BodyElements<String>>() {
            @Override
            boolean cancel(boolean mayInterruptIfRunning) {
                return false
            }
        }
        def mapped = ElementsStages.mapElements(stubborn, Function.identity())

        when:
        mapped.toCompletableFuture().cancel(false)
        stubborn.complete(elements)

        then:
        elements.closed
    }

    void "a mapped result is not closed"() {
        given:
        def elements = new RecordingElements()
        def source = new CompletableFuture<BodyElements<String>>()
        def mapped = ElementsStages.mapElements(source, Function.identity())

        when:
        source.complete(elements)

        then:
        mapped.toCompletableFuture().join().is(elements)
        !elements.closed
    }

    void "cancelling the stage of dataStream closes the elements of a response that arrives anyway"() {
        given:
        def elements = new RecordingElements()
        def client = new StubClient()

        when:
        def stage = client.dataStream(HttpRequest.GET("/"))
        stage.toCompletableFuture().cancel(false)
        client.response.complete(ElementsResponse.of(HttpResponse.ok(), elements))

        then:
        elements.closed
    }

    static class StubClient implements AsyncStreamingHttpClient {
        // ignores a cancellation, as a response already on its way does
        final CompletableFuture<HttpResponse<BodyElements<ByteBuffer<?>>>> response = new CompletableFuture<HttpResponse<BodyElements<ByteBuffer<?>>>>() {
            @Override
            boolean cancel(boolean mayInterruptIfRunning) {
                return false
            }
        }

        @Override
        def <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
            return response
        }

        @Override
        def <I, O> CompletionStage<HttpResponse<BodyElements<O>>> exchangeJsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
            throw new UnsupportedOperationException()
        }

        @Override
        def <I, O, E> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean isRunning() {
            return true
        }

        @Override
        void close() {
            // This test client owns no transport resources.
        }
    }

    static class RecordingElements implements BodyElements<String> {
        boolean closed

        @Override
        CompletionStage<Optional<String>> next() {
            return CompletableFuture.completedFuture(Optional.empty())
        }

        @Override
        CompletionStage<Void> forEach(Function<? super String, ? extends CompletionStage<?>> consumer) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletionStage<Void> closeAsync() {
            closed = true
            return CompletableFuture.completedFuture(null)
        }

        @Override
        void close() {
            closed = true
        }
    }
}
