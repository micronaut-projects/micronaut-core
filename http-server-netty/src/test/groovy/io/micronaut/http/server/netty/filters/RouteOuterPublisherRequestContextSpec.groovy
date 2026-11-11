package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.filter.FilterContinuation
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
 * The request is available in the Reactor context of the publisher returned by the route, also
 * when that publisher emits responses whose body is reactive as well.
 */
class RouteOuterPublisherRequestContextSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RouteOuterPublisherRequestContextSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the outer publisher of #path sees the request in the reactor context"() {
        expect:
        client.toBlocking().retrieve("/outer-context/$path") == "/outer-context/$path"

        where:
        path << [
                "flux-reactive-body",
                "flux-plain-body",
                "mono-reactive-body",
                "mono-plain-body",
                "flux-reactive-body-io",
                "mono-reactive-body-io",
                "filtered-flux-reactive-body",
                "filtered-mono-reactive-body",
        ]
    }

    private static String path(ctx) {
        HttpRequest<?> request = ctx.get(ServerRequestContext.KEY)
        return request.path
    }

    @Requires(property = 'spec.name', value = 'RouteOuterPublisherRequestContextSpec')
    @Controller("/outer-context")
    static class OuterContextController {

        @Get(value = "/flux-reactive-body", produces = "text/plain")
        Flux<HttpResponse<Mono<String>>> fluxReactiveBody() {
            return Flux.deferContextual { ctx -> Flux.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }

        @Get(value = "/flux-plain-body", produces = "text/plain")
        Flux<HttpResponse<String>> fluxPlainBody() {
            return Flux.deferContextual { ctx -> Flux.just(HttpResponse.ok(path(ctx))) }
        }

        @Get(value = "/mono-reactive-body", produces = "text/plain")
        Mono<HttpResponse<Mono<String>>> monoReactiveBody() {
            return Mono.deferContextual { ctx -> Mono.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }

        @Get(value = "/mono-plain-body", produces = "text/plain")
        Mono<HttpResponse<String>> monoPlainBody() {
            return Mono.deferContextual { ctx -> Mono.just(HttpResponse.ok(path(ctx))) }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/flux-reactive-body-io", produces = "text/plain")
        Flux<HttpResponse<Mono<String>>> fluxReactiveBodyIo() {
            return Flux.deferContextual { ctx -> Flux.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/mono-reactive-body-io", produces = "text/plain")
        Mono<HttpResponse<Mono<String>>> monoReactiveBodyIo() {
            return Mono.deferContextual { ctx -> Mono.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }

        @Get(value = "/filtered-flux-reactive-body", produces = "text/plain")
        Flux<HttpResponse<Mono<String>>> filteredFluxReactiveBody() {
            return Flux.deferContextual { ctx -> Flux.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }

        @Get(value = "/filtered-mono-reactive-body", produces = "text/plain")
        Mono<HttpResponse<Mono<String>>> filteredMonoReactiveBody() {
            return Mono.deferContextual { ctx -> Mono.just(HttpResponse.ok(Mono.just(path(ctx)))) }
        }
    }

    @Requires(property = 'spec.name', value = 'RouteOuterPublisherRequestContextSpec')
    @ServerFilter(["/outer-context/filtered-*"])
    static class ReactiveFilter {

        @RequestFilter
        Publisher<MutableHttpResponse<?>> filter(FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation) {
            return Mono.from(continuation.proceed())
        }
    }
}
