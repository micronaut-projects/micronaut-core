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
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class FilterRunnerSpec extends Specification {
    def 'simple tasks should not suspend'() {
        given:
        def events = []
        List<InternalFilter> filters = [
                after { req, resp -> events.add("after") },
                before { req -> events.add("before") },
                (InternalFilter.Terminal) (req -> {
                    events.add("terminal")
                    ExecutionFlow.just(HttpResponse.ok())
                })
        ]

        when:
        def result = new FilterRunner(filters).run(HttpRequest.GET("/")).asDone()
        then:
        result != null
        result.value.status() == HttpStatus.OK
        events == ["before", "terminal", "after"]
    }

    def 'around legacy'() {
        given:
        def events = []
        def req1 = HttpRequest.GET("/req1")
        def req2 = HttpRequest.GET("/req2")
        def resp1 = HttpResponse.ok("resp1")
        def resp2 = HttpResponse.ok("resp2")
        List<InternalFilter> filters = [
                new InternalFilter.AroundLegacy(
                        new HttpServerFilter() {
                            @Override
                            Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                                assert request == req1
                                events.add("before")
                                return Flux.from(chain.proceed(req2)).map(resp -> {
                                    assert resp == resp1
                                    events.add("after")
                                    return resp2
                                })
                            }
                        },
                        new FilterOrder.Fixed(0)
                ),
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

    def 'exception in legacy around: before proceed'() {
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                new InternalFilter.AroundLegacy(
                        new HttpServerFilter() {
                            @Override
                            Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                                throw testExc
                            }
                        },
                        new FilterOrder.Fixed(0)
                ),
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

    def 'exception in legacy around: in proceed transform'() {
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                new InternalFilter.AroundLegacy(
                        new HttpServerFilter() {
                            @Override
                            Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                                return Flux.from(chain.proceed(request)).map(r -> { throw testExc })
                            }
                        },
                        new FilterOrder.Fixed(0)
                ),
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

    def 'exception in legacy around: after proceed, downstream gives normal response'() {
        // don't do this at home
        given:
        def events = []
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                new InternalFilter.AroundLegacy(
                        new HttpServerFilter() {
                            @Override
                            Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                                Flux.from(chain.proceed(request)).subscribe()
                                throw testExc
                            }
                        },
                        new FilterOrder.Fixed(0)
                ),
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

    def 'exception in legacy around: after proceed, downstream gives error'() {
        // don't do this at home
        given:
        def testExc = new RuntimeException("Test exception")
        List<InternalFilter> filters = [
                new InternalFilter.AroundLegacy(
                        new HttpServerFilter() {
                            @Override
                            Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
                                Flux.from(chain.proceed(request)).subscribe()
                                // this exception is logged and dropped
                                throw new RuntimeException("Test exception 2")
                            }
                        },
                        new FilterOrder.Fixed(0)
                ),
                (InternalFilter.Terminal) (req -> {
                    ExecutionFlow.error(testExc)
                })
        ]

        when:
        await(new FilterRunner(filters).run(HttpRequest.GET("/")))
        then:
        def actual = thrown Exception
        actual == testExc
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

    private def after(Closure<?> closure) {
        return new InternalFilter.After<>(null, new LambdaExecutable(closure), new FilterOrder.Fixed(0))
    }

    private def before(Closure<?> closure) {
        return new InternalFilter.Before<>(null, new LambdaExecutable(closure), new FilterOrder.Fixed(0))
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

        LambdaExecutable(Closure<?> closure) {
            this.closure = closure
        }

        @Override
        Class<Object> getDeclaringType() {
            return Object
        }

        @Override
        Argument<?>[] getArguments() {
            return closure.parameterTypes.collect { Argument.of(it) }
        }

        @Override
        Object invoke(@Nullable Object instance, Object... arguments) {
            return closure.curry(arguments)()
        }
    }
}
