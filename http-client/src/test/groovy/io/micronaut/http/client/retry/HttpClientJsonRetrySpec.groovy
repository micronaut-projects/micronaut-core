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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpClientJsonRetrySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpClientJsonRetrySpec'
    ])

    @Shared
    @AutoCleanup
    ApplicationContext context = embeddedServer.applicationContext

    void "test simple blocking retry"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)

        when:"A method is annotated retry"
        Count result = countClient.getCount()

        then:"It executes until successful"
        result.number == 3

        when:"The threshold can never be met"
        controller.countThreshold = 10
        controller.count = 0
        countClient.getCount()

        then:"The original exception is thrown"
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"
    }

    void "test simply retry with rxjava"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3
        controller.count = 0

        when:"A method is annotated retry"
        Count result = Mono.from(countClient.getCountSingle()).block()

        then:"It executes until successful"
        result.number

        when:"The threshold can never be met"
        controller.countThreshold = 10
        controller.count = 0
        Publisher<Integer> single = countClient.getCountSingle()
        Mono.from(single).block()

        then:"The original exception is thrown"
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad count"

    }

    @Requires(property = 'spec.name', value = 'HttpClientJsonRetrySpec')
    @Client("/json-retry-test")
    @Retryable(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Requires(property = 'spec.name', value = 'HttpClientJsonRetrySpec')
    @Controller("/json-retry-test")
    static class CountController implements CountService {
        int count = 0
        int countRx = 0
        int countThreshold = 3

        @Override
        Count getCount() {
            count++
            if(count < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return new Count(number: count)
        }

        @Override
        @SingleResult
        Publisher<Count> getCountSingle() {
            Mono.fromCallable({->
                countRx++
                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return new Count(number:  countRx)
            })

        }
    }

    static class Count {
        int number
    }

    static interface CountService {

        @Get('/count')
        Count getCount()

        @Get('/rx-count')
        @SingleResult
        Publisher<Count> getCountSingle()
    }
}
