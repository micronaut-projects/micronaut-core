package io.micronaut.http.server.netty.mdc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class MdcLegacyFilterDownstreamSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'           : 'MdcLegacyFilterDownstreamSpec',
            'micronaut.propagation': 'thread-local'
    ])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'MDC propagated by a legacy filter is visible to the later filters, the route and the response filters'() {
        given:
        LaterFilter laterFilter = embeddedServer.applicationContext.getBean(LaterFilter)

        when:
        String response = client.toBlocking().retrieve(HttpRequest.GET('/mdc-legacy'))

        then:
        response == '1234567890'
        laterFilter.seenOnRequest.get() == '1234567890'
        laterFilter.seenOnResponse.get() == '1234567890'
    }

    @Controller
    @Requires(property = 'spec.name', value = 'MdcLegacyFilterDownstreamSpec')
    static class TheController {
        @Get('/mdc-legacy')
        String get() {
            return MDC.get('trace_id')
        }
    }

    @Filter(MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'MdcLegacyFilterDownstreamSpec')
    static class LegacyMdcFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            def context = PropagatedContext.getOrEmpty().plus(new MdcPropagationContext(Map.of('trace_id', '1234567890')))
            try (PropagatedContext.Scope ignore = context.propagate()) {
                return chain.proceed(request)
            }
        }

        @Override
        int getOrder() {
            return ServerFilterPhase.FIRST.order()
        }
    }

    @ServerFilter(MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'MdcLegacyFilterDownstreamSpec')
    static class LaterFilter {
        final AtomicReference<String> seenOnRequest = new AtomicReference<>()
        final AtomicReference<String> seenOnResponse = new AtomicReference<>()

        @RequestFilter
        void filterRequest(HttpRequest<?> request) {
            seenOnRequest.set(MDC.get('trace_id'))
        }

        @ResponseFilter
        void filterResponse(MutableHttpResponse<?> response) {
            seenOnResponse.set(MDC.get('trace_id'))
        }
    }
}
