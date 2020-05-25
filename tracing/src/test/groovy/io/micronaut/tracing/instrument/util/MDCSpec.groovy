package io.micronaut.tracing.instrument.util

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MDCSpec extends Specification {

    static final Logger LOG = LoggerFactory.getLogger(MDCSpec.class)

    @Shared @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.test.enabled':true
    ])

    @Shared @AutoCleanup
    RxHttpClient client = RxHttpClient.create(embeddedServer.URL)

    void "test MDC doesn't leak"() {
        given:
        LOG.info ('MDC adapter: {}', MDC.getMDCAdapter())

        expect:
        100.times {
            String traceId = UUID.randomUUID().toString()
            HttpRequest<Object> request = HttpRequest.GET("/mdc-test").header("traceId", traceId)
            String response = client.toBlocking().retrieve(request)
            assert response == traceId
        }
    }

    @Controller
    @Requires(property = 'mdc.test.enabled')
    static class MDCController {

        @Get("/mdc-test")
        HttpResponse<String> getMdc() {
            Map<String, String> mdc = MDC.getCopyOfContextMap() ?: [:]
            String traceId = mdc.get(RequestIdFilter.TRACE_ID_MDC_KEY)
            LOG.info ('traceId: {}', traceId)
            if (traceId == null) {
                throw new IllegalStateException('Missing traceId')
            }
            HttpResponse.ok(traceId)
        }
    }

    @Filter("/**")
    @Requires(property = 'mdc.test.enabled')
    static class RequestIdFilter extends OncePerRequestHttpServerFilter {

        private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class)

        public static final String TRACE_ID_MDC_KEY = "traceId"

        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            String traceIdHeader = request.getHeaders().get("traceId")
            if (MDC.get(TRACE_ID_MDC_KEY) != null) {
                LOG.warn("MDC should have been empty here.")
            }
            LOG.info("Storing traceId in MDC: " + traceIdHeader)
            MDC.put(TRACE_ID_MDC_KEY, traceIdHeader)
            LOG.info('MDC updated')

            return Flowable
                    .fromPublisher(chain.proceed(request))
                    .doFinally{->
                        LOG.info('Removing traceId id from MDC')
                        MDC.clear()
                    }
        }

        @Override
        int getOrder() {
            return -1
        }
    }

}