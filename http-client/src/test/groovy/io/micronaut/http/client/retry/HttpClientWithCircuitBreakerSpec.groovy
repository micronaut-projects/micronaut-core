/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.retry

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.ApplicationContext
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpClientWithCircuitBreakerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpClientWithCircuitBreakerSpec'
    ])

    @Shared
    @AutoCleanup
    ApplicationContext context = embeddedServer.applicationContext

    void "test simple blocking retry"() {
        given:
        CountFilter countFilter = context.getBean(CountFilter)
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)

        when:"A method is annotated retry"
        int result = countClient.getCount()

        then:"It executes new requests until successful"
        result == 3
        result == countFilter.requests.size()

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.countValue = 0
        countFilter.requests.clear()
        countClient.getCount()

        then:"The original exception is thrown"
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"
        controller.countValue == 6
        countFilter.requests.size() == 6

        when:"the method is called again"
        countClient.getCount()

        then:"The value is not incremented because the circuit is open"
        e = thrown(HttpClientResponseException)
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"
        controller.countValue == 6
        countFilter.requests.size() == 6
    }

    void "test simply retry with reactive publisher"() {
        given:
        CountFilter countFilter = context.getBean(CountFilter)
        countFilter.requests.clear()
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3
        controller.countValue = 0

        when:"A method is annotated retry"
        int result = Mono.from(countClient.getCountSingle()).block()

        then:"It executes new requests until successful"
        result == 3
        result == countFilter.requests.size()

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.countRx = 0
        countFilter.requests.clear()
        Publisher<Integer> single = countClient.getCountSingle()
        Mono.from(single).block()

        then:"The original exception is thrown"
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"
        controller.countRx == 6
        countFilter.requests.size() == 6

        when:"The method is called again"
        single = countClient.getCountSingle()
        Mono.from(single).block()

        then:"The value is not incremented because the circuit is open"
        e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"
        controller.countRx == 6
        countFilter.requests.size() == 6
    }

    @Requires(property = 'spec.name', value = 'HttpClientWithCircuitBreakerSpec')
    @ClientFilter("/circuit-breaker-test/**")
    static class CountFilter {

        Set<MutableHttpRequest> requests = new HashSet<>()

        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            requests.add(request)
        }
    }

    @Requires(property = 'spec.name', value = 'HttpClientWithCircuitBreakerSpec')
    @Client("/circuit-breaker-test")
    @CircuitBreaker(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Requires(property = 'spec.name', value = 'HttpClientWithCircuitBreakerSpec')
    @Controller("/circuit-breaker-test")
    static class CountController implements CountService {
        int countValue = 0
        int countRx = 0
        int countThreshold = 3

        @Override
        int getCount() {
            countValue++
            if(countValue < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return countValue
        }

        @Override
        @SingleResult
        Publisher<Integer> getCountSingle() {
            Mono.fromCallable({->
                countRx++
                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countRx
            })

        }
    }

    static interface CountService {

        @Get('/count')
        int getCount()

        @Get('/rx-count')
        @SingleResult
        Publisher<Integer> getCountSingle()
    }
}
