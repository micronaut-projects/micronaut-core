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
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Retryable
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
class HttpClientRetryWithFallbackSpec extends Specification{

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpClientRetryWithFallbackSpec'
    ])

    @Shared
    @AutoCleanup
    ApplicationContext context = embeddedServer.applicationContext

    void "test simple blocking retry"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3

        when:"A method is annotated retry"
        int result = countClient.getCount()

        then:"It executes until successful"
        result == 3

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.count = 0
        countClient.getCount()

        then:"The fallback is called"
        countClient.getCount() == 9999
    }

    void "test simply retry with rxjava"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3
        controller.count = 0

        when:"A method is annotated retry"
        int result = Mono.from(countClient.getCountSingle()).block()

        then:"It executes until successful"
        result == 3

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.count = 0
        Publisher<Integer> single = countClient.getCountSingle()

        then:"The original exception is thrown"
        Mono.from(single).block() == 9999
    }

    @Requires(property = 'spec.name', value = 'HttpClientRetryWithFallbackSpec')
    @Client("/retry-fallback")
    @Retryable(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Requires(property = 'spec.name', value = 'HttpClientRetryWithFallbackSpec')
    @Fallback
    static class CountClientFallback implements CountService {

        @Override
        int getCount() {
            return 9999
        }

        @Override
        @SingleResult
        Publisher<Integer> getCountSingle() {
            return Mono.just(9999)
        }
    }

    @Requires(property = 'spec.name', value = 'HttpClientRetryWithFallbackSpec')
    @Controller("/retry-fallback")
    static class CountController implements CountService {
        int count = 0
        int countRx = 0
        int countThreshold = 3

        @Override
        int getCount() {
            count++
            if(count < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return count
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

        @Get('/reactive-count')
        @SingleResult
        Publisher<Integer> getCountSingle()
    }
}
