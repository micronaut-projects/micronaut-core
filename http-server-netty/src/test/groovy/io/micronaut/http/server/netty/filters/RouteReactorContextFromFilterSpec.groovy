package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A filter that subscribes to the response publisher of its continuation can add values to the
 * Reactor context with {@code contextWrite}, and the route publisher must see them.
 */
class RouteReactorContextFromFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RouteReactorContextFromFilterSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the route sees the reactor context written by a #kind filter"() {
        expect:
        client.toBlocking().retrieve("/tenant/$path") == "acme"

        where:
        kind     | path
        "method" | "method"
        "legacy" | "legacy"
        "method" | "method-io"
        "legacy" | "legacy-io"
    }

    void "a route without such a filter still completes"() {
        expect:
        client.toBlocking().retrieve("/tenant/plain") == "MISSING"
    }

    @Requires(property = 'spec.name', value = 'RouteReactorContextFromFilterSpec')
    @Controller("/tenant")
    static class TenantController {

        @Get(value = "/method", produces = "text/plain")
        Mono<String> method() {
            return Mono.deferContextual { ctx -> Mono.just(ctx.getOrDefault("tenant", "MISSING")) }
        }

        @Get(value = "/legacy", produces = "text/plain")
        Mono<String> legacy() {
            return Mono.deferContextual { ctx -> Mono.just(ctx.getOrDefault("tenant", "MISSING")) }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/method-io", produces = "text/plain")
        Mono<String> methodIo() {
            return Mono.deferContextual { ctx -> Mono.just(ctx.getOrDefault("tenant", "MISSING")) }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/legacy-io", produces = "text/plain")
        Mono<String> legacyIo() {
            return Mono.deferContextual { ctx -> Mono.just(ctx.getOrDefault("tenant", "MISSING")) }
        }

        @Get(value = "/plain", produces = "text/plain")
        Mono<String> plain() {
            return Mono.deferContextual { ctx -> Mono.just(ctx.getOrDefault("tenant", "MISSING")) }
        }
    }

    @Requires(property = 'spec.name', value = 'RouteReactorContextFromFilterSpec')
    @ServerFilter(["/tenant/method", "/tenant/method-io"])
    static class MethodTenantFilter {

        @RequestFilter
        Publisher<MutableHttpResponse<?>> tenant(HttpRequest<?> request, FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation) {
            return Mono.from(continuation.proceed()).contextWrite { ctx -> ctx.put("tenant", "acme") }
        }
    }

    @Requires(property = 'spec.name', value = 'RouteReactorContextFromFilterSpec')
    @Filter(["/tenant/legacy", "/tenant/legacy-io"])
    static class LegacyTenantFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request)).contextWrite { ctx -> ctx.put("tenant", "acme") }
        }
    }
}
