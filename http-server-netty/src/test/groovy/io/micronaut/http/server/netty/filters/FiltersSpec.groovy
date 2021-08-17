package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Inject
import jakarta.inject.Named
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class FiltersSpec extends Specification {

    @Unroll
    void "test filters order and threads for #method"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersSpec.simpleName])
            def applicationContext = server.applicationContext
            TheClient client = applicationContext.getBean(TheClient)
            def filter1 = applicationContext.getBean(Filter1)
            def filter2 = applicationContext.getBean(Filter2)
            def filter4 = applicationContext.getBean(Filter4)
            def filter5 = applicationContext.getBean(Filter5)
            def filter6 = applicationContext.getBean(Filter6)
            def filter7 = applicationContext.getBean(Filter7)

        when:
            def response = client."$method"()
        then:
            response == "OK"
            filter1.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter1.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter1.filterOrder == 1

            filter2.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter2.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter2.filterOrder == 2

            filter4.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter4.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter4.filterOrder == 3

            filter5.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter5.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter5.filterOrder == 4

            filter6.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter6.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter6.filterOrder == 5

            filter7.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter7.mapExecutedOn.startsWith "default-nioEventLoopGroup"
            filter7.filterOrder == 6

        cleanup:
            server.close()
            FiltersOrderCounters.counter.set(0)

        where:
            method << ["get", "getReactive"]
    }

    @Unroll
    void "test filters order and threads with subscribeOn for #method"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersSpec.simpleName, 'enableFilter3': true])
            def applicationContext = server.applicationContext
            TheClient client = applicationContext.getBean(TheClient)
            def filter1 = applicationContext.getBean(Filter1)
            def filter2 = applicationContext.getBean(Filter2)
            def filter3 = applicationContext.getBean(Filter3)
            def filter4 = applicationContext.getBean(Filter4)
            def filter5 = applicationContext.getBean(Filter5)
            def filter6 = applicationContext.getBean(Filter6)
            def filter7 = applicationContext.getBean(Filter7)

        when:
            def response = client."$method"()
        then:
            response == "OK"
            filter1.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter1.mapExecutedOn.startsWith "io-executor"
            filter1.filterOrder == 1

            filter2.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter2.mapExecutedOn.startsWith "io-executor"
            filter2.filterOrder == 2

            filter3.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter3.mapExecutedOn.startsWith "io-executor"
            filter3.filterOrder == 3

            filter4.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter4.mapExecutedOn.startsWith "io-executor"
            filter4.filterOrder == 4

            filter5.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter5.mapExecutedOn.startsWith "io-executor"
            filter5.filterOrder == 5

            filter6.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter6.mapExecutedOn.startsWith "io-executor"
            filter6.filterOrder == 6

            filter7.doFilterExecutedOn.startsWith "default-nioEventLoopGroup"
            filter7.mapExecutedOn.startsWith "io-executor"
            filter7.filterOrder == 7

        cleanup:
            server.close()
            FiltersOrderCounters.counter.set(0)

        where:
            method << ["get", "getReactive"]
    }

    @Ignore("Find a fix")
    void "test filter with exception"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersSpec.simpleName, 'badFilter': true])
            def applicationContext = server.applicationContext
            TheClient client = applicationContext.getBean(TheClient)

        when:
            def response = client.get()
        then:
            // TODO
            response == "OK"

        cleanup:
            server.close()
            FiltersOrderCounters.counter.set(0)
    }

    @Requires(property = 'spec.name', value = 'FiltersSpec')
    @Client("/")
    static interface TheClient {
        @Get("/filters-get")
        String get()

        @Get("/filters-reactive-get")
        String getReactive()
    }

    @Requires(property = 'spec.name', value = 'FiltersSpec')
    @Controller
    static class FiltersController {
        @Get("/filters-get")
        String get(HttpRequest<?> request) {
            if (!ServerRequestContext.currentRequest().isPresent()) {
                throw new IllegalStateException("Server request not present in the context!")
            }
            if (!ServerRequestContext.currentRequest().get().is(request)) {
                throw new IllegalStateException("Server request not the correct request!")
            }
            "OK"
        }

        @Get("/filters-reactive-get")
        Mono<String> getReactive(HttpRequest<?> request) {
            return Mono.fromCallable(new Callable<String>() {
                @Override
                String call() throws Exception {
                    if (!ServerRequestContext.currentRequest().isPresent()) {
                        throw new IllegalStateException("Server request not present in the context!")
                    }
                    if (!ServerRequestContext.currentRequest().get().is(request)) {
                        throw new IllegalStateException("Server request not the correct request!")
                    }
                    "OK"
                }
            })
        }

    }

    static class FiltersOrderCounters {
        public static AtomicInteger counter = new AtomicInteger(0)
    }

    @Order(100) //to show that @Order is not supported
    @Filter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter1 extends AbstractFilter {

        @Override
        int getOrder() {
            1
        }
    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter2 extends AbstractFilter {

        @Override
        int getOrder() {
            2
        }
    }

    @Requires(property = 'enableFilter3', value = 'true')
    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter3 extends AbstractFilter {

        @Inject
        @Named(TaskExecutors.IO)
        Executor executor

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(super.doFilter(request, chain)).subscribeOn(Schedulers.fromExecutor(executor))
        }

        @Override
        int getOrder() {
            return 3
        }
    }

    @Requires(property = 'badFilter', value = 'true')
    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class FilterBadFilter3 extends AbstractFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            throw new RuntimeException("BAM!")
        }

        @Override
        int getOrder() {
            return 3
        }
    }

    @Filter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter4 extends AbstractFilter {

        @Override
        int getOrder() {
            return 4
        }
    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter5 extends AbstractFilter {

        @Override
        int getOrder() {
            return 5
        }
    }

    @Filter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter6 extends AbstractFilter {

        @Override
        int getOrder() {
            return 6
        }
    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersSpec')
    static class Filter7 extends AbstractFilter {

        @Override
        int getOrder() {
            return 7
        }
    }

    static abstract class AbstractFilter implements HttpServerFilter {

        String doFilterExecutedOn
        String mapExecutedOn
        int filterOrder

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            System.out.println("doFilter " + getClass().getSimpleName())
            if (!ServerRequestContext.currentRequest().isPresent()) {
                throw new IllegalStateException("Server request not present in the context!")
            }
            if (!ServerRequestContext.currentRequest().get().is(request)) {
                throw new IllegalStateException("Server request not the correct request!")
            }
            doFilterExecutedOn = Thread.currentThread().name
            filterOrder = FiltersOrderCounters.counter.incrementAndGet()
            Publishers.map(chain.proceed(request), { response ->
                {
                    if (!ServerRequestContext.currentRequest().isPresent()) {
                        throw new IllegalStateException("Server request not present in the context!")
                    }
                    if (!ServerRequestContext.currentRequest().get().is(request)) {
                        throw new IllegalStateException("Server request not the correct request!")
                    }
                    mapExecutedOn = Thread.currentThread().name
                    response
                }
            })
        }
    }
}
