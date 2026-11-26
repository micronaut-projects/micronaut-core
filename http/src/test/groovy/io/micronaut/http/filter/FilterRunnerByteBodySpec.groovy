package io.micronaut.http.filter

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseWrapper
import io.micronaut.http.MutableByteBodyHttpResponse
import io.micronaut.http.MutableHttpHeaders
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.body.BodyElements
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.AvailableByteArrayBody
import io.micronaut.http.bind.DefaultRequestBinderRegistry
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class FilterRunnerByteBodySpec extends Specification {

    static class Tracking<B> extends HttpResponseWrapper<B> implements ByteBodyHttpResponse<B> {
        final CloseableByteBody bytes = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "x".bytes)
        int closed

        Tracking() {
            super(HttpResponse.ok())
        }

        @Override
        ByteBody byteBody() {
            return bytes
        }

        @Override
        void close() {
            closed++
            bytes.close()
        }
    }

    def 'imperative filter replacing a byte body response closes it'() {
        given:
        def original = new Tracking()
        def runner = runner(original, after { req, resp -> HttpResponse.accepted() })

        when:
        def result = run(runner)

        then:
        result.code() == 202
        original.closed == 1
    }

    def 'imperative filter wrapping a byte body response keeps it open'() {
        given:
        def original = new Tracking()
        def runner = runner(original, after { req, resp -> new HttpResponseWrapper(new HttpResponseWrapper(resp)) })

        when:
        def result = run(runner)

        then:
        result instanceof HttpResponseWrapper
        original.closed == 0
    }

    def 'async filter replacing a byte body response closes it'() {
        given:
        def original = new Tracking()
        def runner = runner(original, afterAsync { req, resp -> Mono.just(HttpResponse.accepted()) })

        when:
        def result = run(runner)

        then:
        result.code() == 202
        original.closed == 1
    }

    def 'async filter returning the same bytes keeps them open'() {
        given:
        def original = new Tracking()
        def runner = runner(original, afterAsync { req, resp -> Mono.just(new HttpResponseWrapper(resp)) })

        when:
        run(runner)

        then:
        original.closed == 0
    }

    def 'async filter failing closes the byte body response'() {
        given:
        def original = new Tracking()
        def runner = runner(original, afterAsync { req, resp -> Mono.error(new RuntimeException("boom")) })

        when:
        run(runner)

        then:
        def e = thrown(RuntimeException)
        e.message == "boom"
        original.closed == 1
    }

    def 'a discarded response that holds resources is closed through its wrappers'() {
        given:
        def bytes = new Tracking()
        int elementsClosed = 0
        def elements = BodyElements.of({ CompletableFuture.completedFuture(Optional.empty()) }, { elementsClosed++ })
        def plain = HttpResponse.ok("text")

        when:
        FilterRunner.closeDiscarded(new HttpResponseWrapper<>(bytes))
        FilterRunner.closeDiscarded(new FilterContext(HttpRequest.GET("/"), HttpResponse.ok(elements), PropagatedContext.empty(), false))
        FilterRunner.closeDiscarded(plain)
        FilterRunner.closeDiscarded("not a response")
        FilterRunner.closeDiscarded(null)

        then:
        bytes.closed == 1
        elementsClosed == 1
        plain.body() == "text"
    }

    def 'a discarded mutable wrapper closes the response it wraps'() {
        given:
        def bytes = new Tracking()

        when:
        FilterRunner.closeDiscarded(new MutableWrapper(bytes))

        then:
        bytes.closed == 1
    }

    def 'a discarded mutable byte body response closes the bytes that replaced its own'() {
        given:
        def original = new Tracking()
        def mutable = MutableByteBodyHttpResponse.of(original)
        def replacement = new TrackingBytes()
        mutable.body(replacement)

        when:
        FilterRunner.closeDiscarded(mutable)

        then: 'the original bytes were closed when they were replaced'
        original.closed == 1
        replacement.closed == 1
    }

    def 'a discarded mutable byte body response closes the elements that replaced its bytes'() {
        given:
        def original = new Tracking()
        def mutable = MutableByteBodyHttpResponse.of(original)
        int elementsClosed = 0
        mutable.body(BodyElements.of({ CompletableFuture.completedFuture(Optional.empty()) }, { elementsClosed++ }))

        when:
        FilterRunner.closeDiscarded(new HttpResponseWrapper<>(mutable))

        then:
        original.closed == 1
        elementsClosed == 1
    }

    def 'a body that a mutable wrapper shares with the response it wraps is closed once'() {
        given:
        def replacement = new TrackingBytes()
        def wrapped = HttpResponse.ok(replacement)

        when:
        FilterRunner.closeDiscarded(new MutableWrapper(new MutableWrapper(wrapped)))

        then:
        replacement.closed == 1
    }

    /**
     * A closeable byte body that counts its closes.
     */
    static class TrackingBytes implements CloseableByteBody {
        @Delegate(excludes = ['close'])
        final CloseableByteBody bytes = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "y".bytes)
        int closed

        @Override
        void close() {
            closed++
            bytes.close()
        }
    }

    /**
     * A mutable response that wraps another, with the body of the wrapped response.
     */
    static class MutableWrapper<B> extends HttpResponseWrapper<B> implements MutableHttpResponse<B> {
        MutableWrapper(HttpResponse<B> delegate) {
            super(delegate)
        }

        @Override
        MutableHttpHeaders getHeaders() {
            throw new UnsupportedOperationException()
        }

        @Override
        MutableHttpResponse<B> cookie(Cookie cookie) {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> MutableHttpResponse<T> body(T body) {
            throw new UnsupportedOperationException()
        }

        @Override
        MutableHttpResponse<B> status(int status, CharSequence message) {
            throw new UnsupportedOperationException()
        }
    }

    private static FilterRunner runner(HttpResponse<?> response, GenericHttpFilter filter) {
        return new FilterRunner([filter], (r, c) -> ExecutionFlow.just(response))
    }

    private static HttpResponse<?> run(FilterRunner runner) {
        CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>()
        runner.run(HttpRequest.GET("/")).onComplete((v, e) -> {
            if (e == null) {
                future.complete(v)
            } else {
                future.completeExceptionally(e)
            }
        })
        try {
            return future.get()
        } catch (ExecutionException e) {
            throw e.cause
        }
    }

    private static GenericHttpFilter after(Closure<?> closure) {
        return filter(ReturnType.of(HttpResponse), closure)
    }

    private static GenericHttpFilter afterAsync(Closure<?> closure) {
        return filter(ReturnType.of(Publisher, Argument.of(HttpResponse)), closure)
    }

    private static GenericHttpFilter filter(ReturnType returnType, Closure<?> closure) {
        Argument[] args = [Argument.of(HttpRequest), Argument.of(HttpResponse)]
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, args, returnType), true, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), null)
    }
}
