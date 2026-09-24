package io.micronaut.http.filter

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseWrapper
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
