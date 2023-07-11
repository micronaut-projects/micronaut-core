package io.micronaut.context.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class MDCSpec3 extends Specification {

    private static final Logger LOG = LoggerFactory.getLogger(MDCSpec3)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.test3.enabled': true
    ])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'test MDC does not leak'() {
        given:
        LOG.info('MDC adapter: {}', MDC.getMDCAdapter())

        expect:
        1000.times {
            String traceId = UUID.randomUUID()
            String response = client.toBlocking().retrieve(HttpRequest
                    .GET('/mdc-test')
                    .header('traceId', traceId))
            assert response == traceId
        }
    }

    @Controller
    @Requires(property = 'mdc.test3.enabled')
    static class MDCController {

        @Get(value = '/mdc-test', produces = MediaType.TEXT_PLAIN)
        HttpResponse<Publisher<String>> getMdc() {
            def trace = getTraceIdOrFail()
            return HttpResponse.ok(Publishers.map(Mono.fromCallable({ ->
                // here trace cannot be delegated without micrometer context propagation
                return trace
            }), { String n -> n }))
        }

        private static String getTraceIdOrFail() {
            Map<String, String> mdc = MDC.getCopyOfContextMap() ?: [:]
            String traceId = mdc[RequestIdFilter.TRACE_ID_MDC_KEY]
            LOG.info('traceId: {}', traceId)
            if (traceId == null) {
                throw new IllegalStateException('Missing traceId')
            }
            return traceId
        }
    }

    @Filter(MATCH_ALL_PATTERN)
    @Requires(property = 'mdc.test3.enabled')
    static class RequestIdFilter implements HttpServerFilter {

        private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter)

        private static final String TRACE_ID_MDC_KEY = 'traceId'

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request,
                                                   ServerFilterChain chain) {
            String traceIdHeader = request.headers.get('traceId')
            if (MDC.get(TRACE_ID_MDC_KEY) != null) {
                throw new IllegalStateException('MDC should have been empty here.');
            }
            LOG.info('Storing traceId in MDC: {}', traceIdHeader)
            MDC.put(TRACE_ID_MDC_KEY, traceIdHeader)
            LOG.info 'MDC updated'

            try(PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new MdcPropagationContext()).propagate()) {
                return chain.proceed(request)
            } finally {
                MDC.clear()
            }
        }

        @Override
        int getOrder() {
            return -1
        }
    }
}
