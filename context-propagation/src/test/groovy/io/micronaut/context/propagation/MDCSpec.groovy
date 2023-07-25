package io.micronaut.context.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.core.async.propagation.ReactivePropagation
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.codehaus.groovy.tools.shell.IO
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class MDCSpec extends Specification {

    private static final Logger LOG = LoggerFactory.getLogger(MDCSpec)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.test.enabled': true
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
            HttpRequest<Object> request = HttpRequest
                    .GET('/mdc-test')
                    .header('traceId', traceId)
            String response = client.toBlocking().retrieve(request)
            assert response == traceId
        }
    }

    void 'test MDC propagation in another thread'() {
        when:
        String traceId = UUID.randomUUID()
        HttpRequest<Object> request = HttpRequest
                .GET('/another-thread')
                .header('traceId', traceId)
        String response = client.toBlocking().retrieve(request)
        then:
        response == "Not Empty"
    }

    @Controller
    @Requires(property = 'mdc.test.enabled')
    static class MDCController {

        @Get('/mdc-test')
        HttpResponse<String> getMdc() {
            Map<String, String> mdc = MDC.getCopyOfContextMap() ?: [:]
            String traceId = mdc[RequestIdFilter.TRACE_ID_MDC_KEY]
            LOG.info('traceId: {}', traceId)
            if (traceId == null) {
                throw new IllegalStateException('Missing traceId')
            }
            HttpResponse.ok(traceId)
        }

        @Get('/another-thread')
        @ExecuteOn(TaskExecutors.IO)
        String anotherThread() {
            Map<String, String> mdc = MDC.getCopyOfContextMap() ?: [:]
            return mdc.size() == 0 ? "Empty" : "Not Empty"
        }
    }

    @Filter(MATCH_ALL_PATTERN)
    @Requires(property = 'mdc.test.enabled')
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
            LOG.info('MDC updated')

            try (PropagatedContext.Scope ignore = (PropagatedContext.get() + new MdcPropagationContext()).propagate()) {
                chain.proceed(request)
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
