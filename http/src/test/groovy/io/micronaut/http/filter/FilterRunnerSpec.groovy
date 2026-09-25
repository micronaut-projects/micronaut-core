package io.micronaut.http.filter

import org.jspecify.annotations.Nullable
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.CompletableFutureExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.execution.ImperativeExecutionFlow
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.bind.DefaultRequestBinderRegistry
import io.micronaut.http.context.ServerHttpRequestContext
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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

    def 'before returns an empty publisher'(Publisher<?> result) {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(Publisher, Argument.of(HttpResponse))) { HttpRequest<?> req ->
                    events.add("before")
                    result
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp1
        events == ["before", "terminal"]

        where:
        result << [Flux.empty(), Mono.empty(), Mono.delay(Duration.ofMillis(10)).then(Mono.empty())]
    }

    def 'after returns an empty publisher'(Publisher<?> result) {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(Publisher, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    events.add("after")
                    result
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp1
        events == ["terminal", "after"]

        where:
        result << [Flux.empty(), Mono.empty(), Mono.delay(Duration.ofMillis(10)).then(Mono.empty())]
    }

    def 'around filter returns an empty publisher after proceeding'(boolean flux) {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                around(false) { request, chain ->
                    events.add("before")
                    def downstream = Mono.from(chain.proceed(request)).doOnNext { events.add("after") }
                    flux ? downstream.flux().then(Mono.empty()).flux() : downstream.then(Mono.empty())
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        resp == resp1
        events == ["before", "terminal", "after"]

        where:
        flux << [false, true]
    }

    def 'before returns a null publisher from a non-nullable method'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(Publisher, Argument.of(HttpResponse))) { HttpRequest<?> req ->
                    events.add("before")
                    null
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def e = thrown NullPointerException
        e.message == "Returned publisher must not be null, or mark the method as @Nullable"
        events == ["before"]
    }

    def 'after returns a null publisher from a non-nullable method'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(Publisher, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    events.add("after")
                    null
                }
        ]

        when:
        await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def e = thrown NullPointerException
        e.message == "Returned publisher must not be null, or mark the method as @Nullable"
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

    def 'before returns execution flow request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest))) { req ->
                    assert req == req1
                    events.add("before")
                    ExecutionFlow.just(req2)
                }
        ]

        when:
        def result = filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(req1).tryComplete()
        then:
        result != null
        result.value.status() == HttpStatus.OK
        events == ["before", "terminal"]
    }

    def 'before returns delayed execution flow request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def future = new CompletableFuture<HttpRequest<?>>()
        HttpRequest<?> terminalRequest = null
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest))) { req ->
                    events.add("before")
                    CompletableFutureExecutionFlow.just(future)
                }
        ]
        def runner = new FilterRunner(filters, (filteredRequest, propagatedContext) -> {
            terminalRequest = filteredRequest
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def flow = runner.run(req1)
        then:
        events == ["before"]

        when:
        future.complete(req2)
        await(flow)
        then:
        events == ["before", "terminal"]
        terminalRequest == req2
    }

    def 'before returns execution flow response'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse))) {
                    events.add("before")
                    ExecutionFlow.just(HttpResponse.accepted())
                }
        ]

        when:
        def resp = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/"))).value
        then:
        resp.status() == HttpStatus.ACCEPTED
        events == ["before"]
    }

    def 'before returns execution flow error'() {
        given:
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest))) {
                    ExecutionFlow.error(testExc)
                }
        ]

        when:
        await(filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def e = thrown RuntimeException
        e == testExc
    }

    def 'after returns execution flow response'() {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    assert resp == resp1
                    events.add("after")
                    ExecutionFlow.just(resp2)
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

    def 'around filter with execution flow continuation'(boolean delayed) {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        HttpRequest<?> terminalRequest = null
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    assert request == req1
                    events.add("before")
                    continuation.request(req2).proceed().map { resp ->
                        assert resp == resp1
                        events.add("after")
                        resp2
                    }
                }
        ]
        def runner = new FilterRunner(filters, (filteredRequest, propagatedContext) -> {
            terminalRequest = filteredRequest
            events.add("terminal")
            delayed ? CompletableFutureExecutionFlow.just(CompletableFuture.supplyAsync { resp1 }) : ExecutionFlow.just(resp1)
        })

        when:
        def result = await(runner.run(req1)).value
        then:
        result == resp2
        terminalRequest == req2
        events == ["before", "terminal", "after"]

        where:
        delayed << [false, true]
    }

    def 'execution flow continuation completes imperatively'() {
        given:
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed()
                }
        ]

        when:
        def result = filterRunner(filters, {
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/")).tryComplete()
        then:
        result != null
        result.value == resp1
    }

    def 'execution flow continuation with other return types'(ReturnType returnType, Closure<?> convert) {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                before(returnType, [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    events.add("before")
                    convert(continuation.proceed())
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        result == resp1
        events == ["before", "terminal"]

        where:
        returnType                                                     | convert
        ReturnType.of(Publisher, Argument.of(HttpResponse))            | { ExecutionFlow flow -> ReactiveExecutionFlow.fromFlow(flow).toPublisher() }
        ReturnType.of(CompletableFuture, Argument.of(HttpResponse))    | { ExecutionFlow flow -> flow.toCompletableFuture() }
        ReturnType.of(HttpResponse)                                    | { ExecutionFlow flow -> flow.toCompletableFuture().get() }
    }

    def 'execution flow continuation handles downstream error'() {
        given:
        def testExc = new RuntimeException("Test exception")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed().onErrorResume { e ->
                        assert e == testExc
                        ExecutionFlow.just(resp2)
                    }
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            ExecutionFlow.error(testExc)
        }).run(HttpRequest.GET("/"))).value
        then:
        result == resp2
    }

    def 'execution flow continuation propagates the reactor context'(boolean legacy) {
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
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    events.add('flow')
                    continuation.proceed()
                },
                around(legacy) { request, chain ->
                    return Flux.deferContextual { ctx ->
                        events.add('context 2: ' + ctx.get('value'))
                        Flux.from(chain.proceed(request))
                                .contextWrite { it.put('value', 'around 2') }
                    }
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)]) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed().putInContext('value', 'flow 2')
                },
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
        events == ["context 1: outer", "flow", "context 2: around 1", "terminal: flow 2"]

        where:
        legacy << [false, true]
    }

    def 'async only execution flow filters do not touch reactive code'() {
        given:
        def executor = Executors.newSingleThreadExecutor()
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)], executor) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    assertNotReactive()
                    events.add("around")
                    continuation.proceed()
                },
                before(ReturnType.of(CompletableFuture, Argument.of(HttpRequest))) { req ->
                    assertNotReactive()
                    events.add("async")
                    CompletableFuture.supplyAsync({ req }, executor)
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest)), [Argument.of(HttpRequest<?>)], executor) { req ->
                    assertNotReactive()
                    events.add("flow")
                    ExecutionFlow.just(req)
                },
                after(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    assertNotReactive()
                    events.add("after")
                    CompletableFutureExecutionFlow.just(CompletableFuture.supplyAsync({ resp }, executor))
                },
        ]

        when:
        def flow = filterRunner(filters, {
            assertNotReactive()
            events.add("terminal")
            CompletableFutureExecutionFlow.just(CompletableFuture.supplyAsync({ HttpResponse.ok() }, executor))
        }).run(HttpRequest.GET("/"))
        def result = await(flow).value
        then:
        !(flow instanceof ReactiveExecutionFlow)
        result.status() == HttpStatus.OK
        events == ["around", "async", "flow", "terminal", "after"]

        cleanup:
        executor.shutdown()
    }

    def 'reactor context passes execution flow filters running on an executor'(boolean legacy) {
        given:
        def executor = Executors.newSingleThreadExecutor()
        def events = []
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    Flux.from(chain.proceed(request)).contextWrite { it.put('value', 'around') }
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, ExecutionFlow)], executor) { request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    events.add("around flow")
                    continuation.proceed()
                },
                before(ReturnType.of(CompletableFuture, Argument.of(HttpRequest))) { req ->
                    events.add("async")
                    CompletableFuture.supplyAsync({ req }, executor)
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest)), [Argument.of(HttpRequest<?>)], executor) { req ->
                    events.add("flow")
                    ExecutionFlow.just(req)
                },
        ]

        when:
        def result = await(filterRunner(filters, {
            ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(ctx -> {
                events.add('terminal: ' + ctx.getOrDefault('value', 'missing'))
                Mono.just(HttpResponse.ok())
            }))
        }).run(HttpRequest.GET("/"))).value
        then:
        result.status() == HttpStatus.OK
        events == ["around flow", "async", "flow", "terminal: around"]

        cleanup:
        executor.shutdown()

        where:
        legacy << [false, true]
    }

    def 'before returns a nullable response that completes later'(Class<?> type, Closure<?> result) {
        given:
        def events = []
        def future = new CompletableFuture<HttpResponse<?>>()
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(type, nullableArgument(HttpResponse))) { req ->
                    events.add("before")
                    result(future)
                }
        ]

        when:
        def flow = filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/"))
        future.complete(null)
        def resp = await(flow).value
        then:
        resp.status() == HttpStatus.OK
        events == ["before", "terminal"]

        where:
        type              | result
        CompletableFuture | { CompletableFuture f -> f }
        ExecutionFlow     | { CompletableFuture f -> CompletableFutureExecutionFlow.just(f) }
        ExecutionFlow     | { CompletableFuture f -> ReactiveExecutionFlow.fromPublisher(Mono.fromFuture(f)) }
    }

    def 'before returns a non-nullable completion stage that completes later with null'() {
        given:
        def future = new CompletableFuture<HttpRequest<?>>()
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletableFuture, Argument.of(HttpRequest))) { req ->
                    future
                }
        ]

        when:
        def flow = filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/"))
        future.complete(null)
        await(flow)
        then:
        def e = thrown NullPointerException
        e.message == "Returned request must not be null, or mark the method as @Nullable"
    }

    def 'an empty execution flow proceeds with the current request'(Closure<ExecutionFlow<?>> empty) {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        HttpRequest<?> terminalRequest = null
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest))) { req ->
                    events.add("before")
                    empty()
                }
        ]
        def runner = new FilterRunner(filters, (filteredRequest, propagatedContext) -> {
            terminalRequest = filteredRequest
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def result = await(runner.run(req1)).value
        then:
        result.status() == HttpStatus.OK
        terminalRequest == req1
        events == ["before", "terminal"]

        where:
        empty << [
                { ExecutionFlow.empty() },
                { CompletableFutureExecutionFlow.just(CompletableFuture.completedFuture(null)) },
                { ReactiveExecutionFlow.fromPublisher(Mono.empty()) }
        ]
    }

    def 'an empty execution flow from a continuation proceeds with the downstream response'() {
        given:
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(FilterContinuation, ExecutionFlow)]) { FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed().flatMap { ExecutionFlow.empty() }
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/"))).value
        then:
        result == resp1
    }

    def 'a null execution flow fails when the method is not nullable'() {
        given:
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest))) { req ->
                    null
                }
        ]

        when:
        await(filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")))
        then:
        def e = thrown NullPointerException
        e.message == "Returned flow must not be null, or mark the method as @Nullable"
    }

    def 'reactive backed result keeps the propagated context'(boolean flowReturn) {
        given:
        def element = new TestContextElement()
        def propagatedContext = PropagatedContext.empty().plus(element)
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(flowReturn ? ExecutionFlow : Publisher, Argument.of(HttpResponse))) { req ->
                    def publisher = Mono.deferContextual { ctx ->
                        assert ReactorPropagation.findContextElement(ctx, TestContextElement).orElse(null).is(element)
                        Mono.just(HttpResponse.ok())
                    }
                    flowReturn ? ReactiveExecutionFlow.fromPublisher(publisher) : publisher
                }
        ]

        expect:
        await(filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/"), propagatedContext)).value.status() == HttpStatus.OK

        where:
        flowReturn << [false, true]
    }

    def 'reactor context written around a continuation passes executor filters'(boolean flowContinuation, String operator, Closure<?> compose) {
        given:
        def executor = Executors.newSingleThreadExecutor()
        def events = []
        def continuationType = flowContinuation ? ExecutionFlow : Publisher
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(continuationType, Argument.of(HttpResponse)), [Argument.of(FilterContinuation, continuationType)]) { continuation ->
                    def publisher = Mono.defer {
                        def next = continuation.proceed()
                        flowContinuation ? Mono.from(ReactiveExecutionFlow.toPublisher(compose(next))) : Mono.from(next)
                    }.contextWrite { it.put('value', 'around') }
                    flowContinuation ? ReactiveExecutionFlow.fromPublisher(publisher) : publisher
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest)), [Argument.of(HttpRequest)], executor) { req ->
                    ExecutionFlow.just(req)
                }
        ]

        when:
        await(filterRunner(filters, {
            ReactiveExecutionFlow.fromPublisher(Mono.deferContextual { ctx ->
                events.add(ctx.getOrDefault('value', 'missing'))
                Mono.just(HttpResponse.ok())
            })
        }).run(HttpRequest.GET('/')))
        then:
        events == ['around']

        cleanup:
        executor.shutdown()

        where:
        flowContinuation | operator        | compose
        false            | 'none'          | { it }
        true             | 'none'          | { it }
        true             | 'map'           | { ExecutionFlow f -> f.map { it } }
        true             | 'flatMap'       | { ExecutionFlow f -> f.flatMap { ExecutionFlow.just(it) } }
        true             | 'onErrorResume' | { ExecutionFlow f -> f.onErrorResume { ExecutionFlow.error(it) } }
        true             | 'then'          | { ExecutionFlow f -> f.then { ExecutionFlow.just(HttpResponse.ok()) } }
        true             | 'chained'       | { ExecutionFlow f -> f.map { it }.putInContext('other', 'value').flatMap { ExecutionFlow.just(it) } }
    }

    def 'operators on a continuation flow call the downstream once'() {
        given:
        def calls = 0
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(FilterContinuation, ExecutionFlow)]) { FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    def next = continuation.proceed()
                    def first = next.map { it }
                    def second = next.map { it }
                    assert calls == 0
                    first.tryComplete()
                    second.tryComplete()
                    first
                }
        ]

        when:
        def result = await(filterRunner(filters, {
            calls++
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET('/'))).value
        then:
        result.status() == HttpStatus.OK
        calls == 1
    }

    def 'execution flow continuation calls the downstream without reactive code when used as a flow'() {
        given:
        def executor = Executors.newSingleThreadExecutor()
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpResponse)), [Argument.of(FilterContinuation, ExecutionFlow)]) { FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed().map { it }
                },
                before(ReturnType.of(ExecutionFlow, Argument.of(HttpRequest)), [Argument.of(HttpRequest)], executor) { req ->
                    assertNotReactive()
                    ExecutionFlow.just(req)
                }
        ]

        when:
        def flow = filterRunner(filters, {
            assertNotReactive()
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET('/'))
        def result = await(flow).value
        then:
        !(flow instanceof ReactiveExecutionFlow)
        result.status() == HttpStatus.OK

        cleanup:
        executor.shutdown()
    }

    static class TestContextElement implements PropagatedContextElement {
    }

    def 'resolved completion stage request is unwrapped without suspending'(Closure<CompletionStage<?>> stage) {
        given:
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        HttpRequest<?> terminalRequest = null
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletionStage, Argument.of(HttpRequest))) { req ->
                    stage(req2)
                }
        ]
        def runner = new FilterRunner(filters, (filteredRequest, propagatedContext) -> {
            terminalRequest = filteredRequest
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def result = runner.run(req1).tryComplete()
        then:
        result != null
        result.value.status() == HttpStatus.OK
        terminalRequest == req2

        where:
        stage << [
                { r -> CompletableFuture.completedFuture(r) },
                { r -> CompletableFuture.completedStage(r) },
                { r -> CompletableFuture.completedFuture("ignored").thenApply { r } }
        ]
    }

    def 'resolved nullable completion stage proceeds without suspending'() {
        given:
        def events = []
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletionStage, nullableArgument(HttpResponse))) { req ->
                    events.add("before")
                    CompletableFuture.completedFuture(null)
                }
        ]

        when:
        def result = filterRunner(filters, {
            events.add("terminal")
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")).tryComplete()
        then:
        result != null
        result.value.status() == HttpStatus.OK
        events == ["before", "terminal"]
    }

    def 'resolved completion stage response is unwrapped without suspending'() {
        given:
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                after(ReturnType.of(CompletionStage, Argument.of(HttpResponse))) { HttpResponse<?> resp ->
                    assert resp == resp1
                    CompletableFuture.completedStage(resp2)
                }
        ]

        when:
        def result = filterRunner(filters, {
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/")).tryComplete()
        then:
        result != null
        result.value == resp2
    }

    def 'resolved completion stage error is unwrapped without suspending'() {
        given:
        def testExc = new RuntimeException("Test exception")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletionStage, Argument.of(HttpRequest))) { req ->
                    CompletableFuture.failedStage(testExc)
                }
        ]

        when:
        def result = filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok())
        }).run(HttpRequest.GET("/")).tryComplete()
        then:
        result != null
        result.error == testExc
    }

    def 'continuation completion stage from an imperative downstream is unwrapped without suspending'() {
        given:
        def resp1 = HttpResponse.ok("resp1")
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(CompletionStage, Argument.of(HttpResponse)), [Argument.of(FilterContinuation, ExecutionFlow)]) { FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation ->
                    continuation.proceed().toCompletableFuture()
                }
        ]

        when:
        def result = filterRunner(filters, {
            ExecutionFlow.just(resp1)
        }).run(HttpRequest.GET("/")).tryComplete()
        then:
        result != null
        result.value == resp1
    }

    private static Argument<?> nullableArgument(Class<?> type) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, [:])
        return Argument.of(type, (AnnotationMetadata) metadata, new Argument[0])
    }

    private static void assertNotReactive() {
        def reactorFrames = new Throwable().stackTrace.findAll { it.className.startsWith('reactor.') }
        assert reactorFrames.isEmpty()
    }

    def 'pass-through around filter keeps the flow imperative'(boolean legacy) {
        given:
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return chain.proceed(request)
                }
        ]

        when:
        def flow = filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok("resp1"))
        }).run(HttpRequest.GET("/req1"))
        def result = flow.tryComplete()
        then:
        result != null
        result.value.status() == HttpStatus.OK
        result.value.body() == "resp1"

        where:
        legacy << [false, true]
    }

    def 'around filter returning an immediate response keeps the flow imperative'(boolean legacy) {
        given:
        def resp2 = HttpResponse.ok("resp2")
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return Mono.just(resp2)
                }
        ]

        when:
        def flow = filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok("resp1"))
        }).run(HttpRequest.GET("/req1"))
        def result = flow.tryComplete()
        then:
        result != null
        result.value == resp2

        where:
        legacy << [false, true]
    }

    def 'request context is visible in the continuation publisher'(boolean legacy) {
        given:
        def req = HttpRequest.GET("/req1")
        def seen = []
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return Mono.from(chain.proceed(request))
                            .flatMap { resp ->
                                Mono.fromCallable {
                                    seen.add(ServerRequestContext.currentRequest().orElse(null))
                                    resp
                                }
                            }
                            .flatMap { resp ->
                                Mono.deferContextual { ctx ->
                                    seen.add(ServerRequestContext.currentRequest(ctx).orElse(null))
                                    Mono.just(resp)
                                }
                            }
                }
        ]

        when:
        def runner = filterRunner(filters, {
            ExecutionFlow.just(HttpResponse.ok("resp1"))
        })
        def result = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(req)).propagate({
            await(runner.run(req))
        } as Supplier)
        then:
        result.value.status() == HttpStatus.OK
        seen == [req, req]

        where:
        legacy << [false, true]
    }

     def 'a filter subscribing to the continuation publisher marks the context of the response provider'(boolean legacy) {
        given:
        List<GenericHttpFilter> filters = [
                around(legacy) { request, chain ->
                    return Mono.from(chain.proceed(request)).contextWrite { it.put('tenant', 'acme') }
                }
        ]
        def marked = []

        when:
        def result = await(new FilterRunner(filters, (request, propagatedContext) -> {
            marked.add(ReactiveFilterChainElement.isPresent(propagatedContext))
            // the provider keeps the result lazy when marked, so the context of the filter is visible
            return ReactiveExecutionFlow.fromPublisher(Mono.deferContextual { ctx -> Mono.just(HttpResponse.ok(ctx.getOrDefault('tenant', 'MISSING'))) })
        }).run(HttpRequest.GET("/req1")))
        then:
        marked == [true]
        result.value.body() == 'acme'

        where:
        legacy << [false, true]
    }

    def 'other filters do not mark the context of the response provider'() {
        given:
        List<GenericHttpFilter> filters = [
                before(ReturnType.of(void)) { req -> null },
                before(ReturnType.of(HttpResponse), [Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, HttpResponse)]) { request, chain ->
                    return chain.proceed()
                },
                after(ReturnType.of(void)) { req, resp -> null },
        ]
        def marked = []

        when:
        def flow = new FilterRunner(filters, (request, propagatedContext) -> {
            marked.add(ReactiveFilterChainElement.isPresent(propagatedContext))
            return ExecutionFlow.just(HttpResponse.ok("resp1"))
        }).run(HttpRequest.GET("/req1"))
        then:
        marked == [false]
        flow.tryComplete().value.body() == "resp1"
    }

    private def after(ReturnType returnType, List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, arguments.toArray(new Argument[0]), returnType), true, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), null)
    }

    private def before(ReturnType returnType, List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return before(returnType, arguments, null, closure)
    }

    private def before(ReturnType returnType, List<Argument> arguments, @Nullable Executor executor, Closure<?> closure) {
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, arguments.toArray(new Argument[0]), returnType), false, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), executor)
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

}
