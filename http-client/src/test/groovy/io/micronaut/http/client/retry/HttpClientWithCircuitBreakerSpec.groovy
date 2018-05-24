/*
 * Copyright 2017-2018 original authors
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

import io.reactivex.Single
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.runtime.server.EmbeddedServer
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
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test simple blocking retry"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)

        when:"A method is annotated retry"
        int result = countClient.getCount()

        then:"It executes until successful"
        result == 3

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.countValue = 0
        countClient.getCount()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
        controller.countValue == 6

        when:"the method is called again"

        countClient.getCount()

        then:"The value is not incremented because the circuit is open"
        e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
        controller.countValue == 6

    }

    void "test simply retry with rxjava"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3
        controller.countValue = 0

        when:"A method is annotated retry"
        int result = countClient.getCountSingle().blockingGet()

        then:"It executes until successful"
        result == 3

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.countRx = 0
        def single = countClient.getCountSingle()
        single.blockingGet()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
        controller.countRx == 6

        when:"The method is called again"
        single = countClient.getCountSingle()
        single.blockingGet()

        then:"The value is not incremented because the circuit is open"
         e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
        controller.countRx == 6
    }

    @Client("/circuit-breaker-test")
    @CircuitBreaker(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

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
        Single<Integer> getCountSingle() {
            Single.fromCallable({->
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
        Single<Integer> getCountSingle()
    }
}
