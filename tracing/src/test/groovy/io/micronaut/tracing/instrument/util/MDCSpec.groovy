/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.micronaut.reactive.rxjava2.ConditionalInstrumentedPublisher
import io.micronaut.reactive.rxjava2.ConditionalInstrumenter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MDCSpec extends Specification {

    @Shared @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.test.enabled': true
    ])

    @Shared @AutoCleanup
    RxHttpClient client = RxHttpClient.create(embeddedServer.URL)


    void "test MDC doesn't leak"() {
        expect:
        100.times {
            String traceId = UUID.randomUUID().toString()
            HttpRequest<Object> request = HttpRequest.GET("/mdc-test").header("traceId", traceId)
            String response = client.retrieve(request).blockingFirst()
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
            return traceId == null ? HttpResponse.notFound("") : HttpResponse.ok(traceId)
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
            Publisher<MutableHttpResponse<?>> publisher = chain.proceed(request)
            ConditionalInstrumenter instrumenter = new MdcKeyValuePairInstrumenter(TRACE_ID_MDC_KEY, traceIdHeader)
            return new ConditionalInstrumentedPublisher<>(publisher, instrumenter)
        }

        @Override
        int getOrder() {
            return -1
        }
    }

}
