package io.micronaut.http.filter

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.execution.CompletableFutureExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.execution.ImperativeExecutionFlow
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Executable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.util.context.Context
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class FilterRunnerSpec extends Specification {
    def 'simple tasks should not suspend'() {
        given:
        def events = []
        List<InternalFilter> filters = [
                after { req, resp ->
                    events.add("after")
                    null
                },
                before { req ->
                    events.add("before")
                    null
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        def result = new FilterRunner(filters).run(HttpRequest.GET("/")).asDone().value
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
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    assert request == req1
                    events.add("before")
                    return Flux.from(chain.proceed(req2)).map(resp -> {
                        assert resp == resp1
                        events.add("after")
                        return resp2
                    })
                },
                (InternalFilter.Terminal) (req -> {
                    assert req == req2
                    events.add("terminal")
                    ExecutionFlow.just(resp1)
                })
        ]

        when:
        def result = await(new FilterRunner(filters).run(req1))
        then:
        result != null
        events == ["before", "terminal", "after"]

        where:
        legacy << [true, false]
    }

    def 'around filter context propagation'(boolean legacy) {
        given:
        def events = []
        List<InternalFilter> filters = [
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
                },
                (InternalFilter.TerminalWithReactorContext) ((req, ctx) -> {
                    events.add('terminal: ' + ctx.get('value'))
                    ExecutionFlow.just(HttpResponse.ok("resp1"))
                })
        ]

        when:
        def runner = new FilterRunner(filters)
        runner.reactorContext(Context.of('value', 'outer'))
        def result = await(runner.run(HttpRequest.GET("/req1")))
        then:
        result != null
        events == ["context 1: outer", "context 2: around 1", "terminal: around 2"]

        where:
        legacy << [false, true]
    }

    def 'exception in before'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                before { throw testExc },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == []
    }

    def 'exception in after'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                after { req, resp -> throw testExc },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]
    }

    def 'exception in terminal: direct'() {
        given:
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                (InternalFilter.Terminal) (req -> {
                    throw testExc
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
    }

    def 'exception in terminal: flow'() {
        given:
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                (InternalFilter.Terminal) (req -> {
                    return ExecutionFlow.error(testExc)
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
    }

    def 'exception in around: before proceed'(boolean legacy) {
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    throw testExc
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
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
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    return Flux.from(chain.proceed(request)).map(r -> { throw testExc })
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
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
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    Flux.from(chain.proceed(request)).subscribe()
                    throw testExc
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
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
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    Flux.from(chain.proceed(request)).subscribe()
                    throw testExc
                },
                (InternalFilter.Terminal) (req -> {
                    CompletableFutureExecutionFlow.just(terminalFuture)
                })
        ]

        when:
        def flow = new FilterRunner(filters).run(HttpRequest.GET("/"))
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
        List<InternalFilter> filters = [
                around(legacy) { request, chain ->
                    events.add("around")
                    Flux.just(HttpResponse.ok("foo"))
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        def resp = await(new FilterRunner(filters).run(HttpRequest.GET("/"))).value
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
        List<InternalFilter> filters = [
                before { req ->
                    assert req == req1
                    events.add("before")
                    req2
                },
                (InternalFilter.Terminal) (req -> {
                    assert req == req2
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns response'() {
        given:
        def events = []
        List<InternalFilter> filters = [
                before {
                    events.add("before")
                    HttpResponse.ok()
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        events == ["before"]
    }

    def 'before returns publisher request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<InternalFilter> filters = [
                before { req ->
                    assert req == req1
                    events.add("before")
                    Flux.just(req2)
                },
                (InternalFilter.Terminal) (req -> {
                    assert req == req2
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns completablefuture request'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        List<InternalFilter> filters = [
                before { req ->
                    assert req == req1
                    events.add("before")
                    CompletableFuture.completedFuture(req2)
                },
                (InternalFilter.Terminal) (req -> {
                    assert req == req2
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(req1))
        then:
        events == ["before", "terminal"]
    }

    def 'before returns publisher response'() {
        given:
        def events = []
        List<InternalFilter> filters = [
                before {
                    events.add("before")
                    Flux.just(HttpResponse.ok())
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        events == ["before"]
    }

    def 'after returns new response'() {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<InternalFilter> filters = [
                after { HttpResponse<?> resp ->
                    assert resp == resp1
                    events.add("after")
                    resp2
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(resp1)
                })
        ]

        when:
        def resp = await(new FilterRunner(filters).run(HttpRequest.GET("/"))).value
        then:
        resp == resp2
        events == ["terminal", "after"]
    }

    def 'after returns publisher response'() {
        given:
        def events = []
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<InternalFilter> filters = [
                after { HttpResponse<?> resp ->
                    assert resp == resp1
                    events.add("after")
                    Flux.just(resp2)
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(resp1)
                })
        ]

        when:
        def resp = await(new FilterRunner(filters).run(HttpRequest.GET("/"))).value
        then:
        resp == resp2
        events == ["terminal", "after"]
    }

    def 'after should not be called if there is an exception but it cannot handle exceptions'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                after {
                    events.add("after")
                    null
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.error(testExc)
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
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
        List<InternalFilter> filters = [
                after { Exception exc ->
                    assert exc == testExc
                    events.add("after")
                    resp1
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.error(testExc)
                })
        ]

        when:
        def resp = await(new FilterRunner(filters).run(HttpRequest.GET("/"))).value
        then:
        resp == resp1
        events == ["terminal", "after"]
    }

    def 'after should not be called if there is an exception it cannot handle'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                after { RuntimeException exc ->
                    events.add("after")
                    null
                },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.error(testExc)
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
        events == ["terminal"]
    }

    def 'async filter'() {
        given:
        def events = []
        def testExc = new Exception("Test exception")
        List<InternalFilter> filters = [
                new InternalFilter.Async(before {
                    events.add("before " + Thread.currentThread().name)
                    null
                }, Executors.newCachedThreadPool(new ThreadFactory() {
                    @Override
                    Thread newThread(Runnable r) {
                        return new Thread(r, "thread-before")
                    }
                })),
                new InternalFilter.Async(after {
                    events.add("after " + Thread.currentThread().name)
                    null
                }, Executors.newCachedThreadPool(new ThreadFactory() {
                    @Override
                    Thread newThread(Runnable r) {
                        return new Thread(r, "thread-after")
                    }
                })),
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal " + Thread.currentThread().name)
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        def response = await(new FilterRunner(filters).run(HttpRequest.GET("/"))).value
        then:
        response.status() == HttpStatus.OK
        events == ["before thread-before", "terminal thread-before", "after thread-after"]
    }

    def 'around filter with blocking continuation'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<InternalFilter> filters = [
                before([Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, HttpResponse)]) { request, chain ->
                    assert request == req1
                    events.add("before")
                    def resp = chain.proceed(req2)
                    assert resp == resp1
                    events.add("after")
                    return resp2
                },
                (InternalFilter.Terminal) (req -> {
                    assert req == req2
                    events.add("terminal")
                    ExecutionFlow.just(resp1)
                })
        ]

        when:
        def result = await(new FilterRunner(filters).run(req1))
        then:
        result != null
        events == ["before", "terminal", "after"]
    }

    private def after(List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return new InternalFilter.After<>(null, new LambdaExecutable(closure, arguments.toArray(new Argument[0])), new FilterOrder.Fixed(0))
    }

    private def before(List<Argument> arguments = closure.parameterTypes.collect { Argument.of(it) }, Closure<?> closure) {
        return new InternalFilter.Before<>(null, new LambdaExecutable(closure, arguments.toArray(new Argument[0])), new FilterOrder.Fixed(0))
    }

    private def around(boolean legacy, Closure<Publisher<MutableHttpResponse<?>>> closure) {
        if (legacy) {
            return new InternalFilter.AroundLegacy(
                    new HttpServerFilter() {
                        @Override
                        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                            return closure(request, chain)
                        }
                    },
                    new FilterOrder.Fixed(0)
            )
        } else {
            return before([Argument.of(HttpRequest<?>), Argument.of(FilterContinuation, Publisher)], closure)
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
        return CompletableFutureExecutionFlow.just(future).asDone()
    }

    private static class LambdaExecutable implements Executable<Object, Object> {
        private final Closure<?> closure
        private final Argument<?>[] arguments;

        LambdaExecutable(Closure<?> closure, Argument<?>[] arguments) {
            this.closure = closure
            this.arguments = arguments
        }

        @Override
        Class<Object> getDeclaringType() {
            return Object
        }

        @Override
        Argument<?>[] getArguments() {
            return arguments;
        }

        @Override
        Object invoke(@Nullable Object instance, Object... arguments) {
            return closure.curry(arguments)()
        }
    }
}
