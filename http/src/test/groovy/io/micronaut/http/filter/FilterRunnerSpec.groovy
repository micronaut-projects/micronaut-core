package io.micronaut.http.filter

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.CompletableFutureExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.execution.ImperativeExecutionFlow
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.bind.DefaultRequestBinderRegistry
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow
import io.micronaut.inject.ExecutableMethod
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.Supplier

class FilterRunnerSpec extends Specification {
    private FilterRunner filterRunner(List<GenericHttpFilter> filters, Supplier<ExecutionFlow<HttpResponse>> responseProvider) {
        return new FilterRunner(filters, (filteredRequest, propagatedContext) -> responseProvider.get())
    }

    def 'simple tasks should not suspend'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(void)) { req, resp ->
                    events.add("after")
                    null
                },
                before(ReturnType.of(void)) { req ->
                    events.add("before")
                    null
                }
        ]

        when:
        def result = filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")).tryComplete().value
        then:
        result.status() == HttpStatus.OK
        events == ["before", "terminal", "after"]
    }

    def 'around filter'(boolean legacy) {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    assert request == req1
                    events.add("before")
                    return Flux.from(chain.proceed(req2)).map(resp -> {
                        assert resp == resp1
                        events.add("after")
                        return resp2
                    })
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(req1))
        then:
        result != null
        events == ["before", "terminal", "after"]

        where:
        legacy << [true, false]
    }

    def 'around filter context propagation'(boolean legacy) {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return Flux.deferContextual { ctx ->
                        events.add('context 1: ' + ctx.get('value'))
                        Flux.from(chain.proceed(request))
                                .contextWrite { it.put('value', 'around 1') }
                    }
                },
                around(legacy) { request, chain ->
                    return Flux.deferContextual { ctx ->
                        events.add('context 2: ' + ctx.get('value'))
                        Flux.from(chain.proceed(request))
                            .contextWrite { it.put('value', 'around 2') }
                    }
                }
        ]

        when:
        def runner = filterRunner(filters, {
            return ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(ctx -> {
                events.add('terminal: ' + ctx.get('value'))
                Mono.just(HttpResponse.ok("resp1"))
            }))
        })
        def result = await(
                ReactiveExecutionFlow.fromFlow(
                        runner.run(HttpRequest.GET("/req1"))
                ).putInContext('value', 'outer')
        )
        then:
        result != null
        events == ["context 1: outer", "context 2: around 1", "terminal: around 2"]

        where:
        legacy << [false, true]
    }

    def 'around filters invocation order'(boolean legacy) {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    events.add('before 1')
                    return Flux.deferContextual { ctx ->
                        events.add('context 1: ' + ctx.get('value'))
                        Flux.from(chain.proceed(request))
                                .doOnNext { events.add('next 1') }
                                .contextWrite { it.put('value', 'around 1') }
                    }
                },
                around(legacy) { request, chain ->
                    events.add('before 2')
                    return Flux.deferContextual { ctx ->
                        events.add('context 2: ' + ctx.get('value'))
                        Flux.from(chain.proceed(request))
                                .doOnNext { events.add('next 2') }
                                .contextWrite { it.put('value', 'around 2') }
                    }
                }
        ]

        when:
        def runner = filterRunner(filters, {
            return ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(ctx -> {
                events.add('terminal: ' + ctx.get('value'))
                Mono.just(HttpResponse.ok("resp1"))
            }))
        })
        def result = await(
                ReactiveExecutionFlow.fromFlow(
                        runner.run(HttpRequest.GET("/req1"))
                ).putInContext('value', 'outer')
        )
        then:
        result != null
        events == ["before 1", "context 1: outer", "before 2", "context 2: around 1", "terminal: around 2", "next 2", "next 1"]

        where:
        legacy << [false, true]
    }

    def 'exception in before'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(void)) { throw testExc }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == []
    }

    def 'exception in after'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(void)) { req, resp -> throw testExc }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]
    }

    def 'exception in terminal: direct'() {
        given:
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = []

        when:
        await(filterRunner(filters, { throw testExc }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
    }

    def 'exception in terminal: flow'() {
        given:
        def testExc = new Exception("Test exception")
        List<GenericHttpFilter> filters = []

        when:
        await(filterRunner(filters, { ExecutionFlow.error(testExc) }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
    }

    def 'exception in around: before proceed'(boolean legacy) {
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    throw testExc
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == []

        where:
        legacy << [true, false]
    }

    def 'exception in around: in proceed transform'(boolean legacy) {
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return Flux.from(chain.proceed(request)).map(r -> { throw testExc })
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]

        where:
        legacy << [true, false]
    }

    def 'exception in around: after proceed, downstream gives normal response'(boolean legacy) {
        // don't do this at home
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    Flux.from(chain.proceed(request)).subscribe()
                    throw testExc
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]

        where:
        legacy << [true, false]
    }

    def 'exception in around: after proceed, downstream gives error'(boolean legacy) {
        // don't do this at home
        given:
        def testExc = new RuntimeException("Test exception")
        def terminalFuture = new CompletableFuture()
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    Flux.from(chain.proceed(request)).subscribe()
                    throw testExc
                }
        ]

        when:
        def flow = filterRunner(filters, {
                CompletableFutureExecutionFlow.just(terminalFuture)
        }).run(HttpRequest.GET("/"))
        // after the run() call, we're suspended waiting for the terminal to finish
        // this exception is logged and dropped
        terminalFuture.completeExceptionally(new RuntimeException("Test exception 2"))
        await(flow)
        then:
        def actual = thrown Exception
        actual == testExc

        where:
        legacy << [true, false]
    }

    def 'around filter does not call proceed'(boolean legacy) {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    events.add("around")
                    Flux.just(HttpResponse.ok("foo"))
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/"))).value
        then:
        resp.status == HttpStatus.OK
        events == ["around"]

        where:
        legacy << [true, false]
    }

    def 'before returns new request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(HttpRequest)) { req ->
                    assert req == req1
                    events.add("before")
                    req2
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns response'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(HttpResponse)) {
                    events.add("before")
                    HttpResponse.ok()
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        events == ["before"]
    }

    def 'before returns publisher request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(Flux, Argument.of(HttpRequest))) { req ->
                    assert req == req1
                    events.add("before")
                    Flux.just(req2)
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns completablefuture request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletableFuture, Argument.of(HttpRequest))) { req ->
                    assert req == req1
                    events.add("before")
                    CompletableFuture.completedFuture(req2)
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns publisher response'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(Flux, Argument.of(HttpResponse))) {
                    events.add("before")
                    Flux.just(HttpResponse.ok())
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        events == ["before"]
    }

    def 'after returns new response'() {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(HttpResponse)) { HttpResponse<?> resp ->
                    assert resp == resp1
                    events.add("after")
                    resp2
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp2
        events == ["terminal", "after"]
    }

    def 'after returns publisher response'() {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(Flux, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    assert resp == resp1
                    events.add("after")
                    Flux.just(resp2)
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp2
        events == ["terminal", "after"]
    }

    def 'after should not be called if there is an exception but it cannot handle exceptions'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(void)) {
                    events.add("after")
                    null
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.error(testExc)
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]
    }

    def 'after should be called if there is an exception that it can handle'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(HttpResponse)) { Exception exc ->
                    assert exc == testExc
                    events.add("after")
                    resp1
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.error(testExc)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp1
        events == ["terminal", "after"]
    }

    def 'after should not be called if there is an exception it cannot handle'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(void)) { RuntimeException exc ->
                    events.add("after")
                    null
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.error(testExc)
        }).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]
    }

    def 'around filter with blocking continuation'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(HttpResponse), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, HttpResponse)]) { request, chain ->
                    assert request == req1
                    events.add("before")
                    def resp = chain.request(req2).proceed()
                    assert resp == resp1
                    events.add("after")
                    return resp2
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(req1))
        then:
        result != null
        events == ["before", "terminal", "after"]
    }

    private def after(ReturnType returnType, List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, arguments.toArray(new Argument[0]), returnType), true, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), null)
    }

    private def before(ReturnType returnType, List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, arguments.toArray(new Argument[0]), returnType), false, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), null)
    }

    private def around(boolean legacy, Closure<Publisher<MutableHttpResponse<?>>> closure) {
        if (legacy) {
            return GenericHttpFilter.createLegacyFilter(
                    new HttpServerFilter() {
                        @Override
                        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                            return closure(request, chain)
                        }
                    },
                    new FilterOrder.Fixed(0)
            )
        } else {
            return before(ReturnType.of(Publisher, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, Publisher)]) { request, continuation ->
                closure(request, new ServerFilterChain() {
                    @Override
                    Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> r) {
                        return continuation.request(r).proceed()
                    }
                })
            }
        }
    }

    private <T> ImperativeExecutionFlow<T> await(ExecutionFlow<T> flow) {
        CompletableFuture<T> future = new CompletableFuture<>()
        flow.onComplete((v, e) -> {
            if (e == null) {
                future.complete(v)
            } else {
                assert !(e instanceof ExecutionException)
                future.completeExceptionally(e)
            }
        })
        try {
            future.get()
        } catch (ExecutionException e) {
            throw e.cause
        }
        return CompletableFutureExecutionFlow.just(future).tryComplete()
    }

    private static class LambdaExecutable implements ExecutableMethod<Object, Object> {
        private final Closure<?> closure
        private final Argument<?>[] arguments
        private final ReturnType<?> returnType

        LambdaExecutable(Closure<?> closure, Argument<?>[] arguments, ReturnType<?> returnType) {
            this.closure = closure
            this.arguments = arguments
            this.returnType = returnType
        }

        @Override
        Class<Object> getDeclaringType() {
            return Object
        }

        @Override
        String getMethodName() {
            throw new UnsupportedOperationException()
        }

        @Override
        Argument<?>[] getArguments() {
            return arguments
        }

        @Override
        Method getTargetMethod() {
            throw new UnsupportedOperationException()
        }

        @Override
        ReturnType<Object> getReturnType() {
            return returnType
        }

        @Override
        Object invoke(@Nullable Object instance, Object... arguments) {
            return closure.curry(arguments)()
        }
    }
}
