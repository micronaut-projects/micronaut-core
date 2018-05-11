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
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.server.EmbeddedServer
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
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

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
        int result = countClient.getCountSingle().blockingGet()

        then:"It executes until successful"
        result == 3

        when:"The threshold can never be met"
        controller.countThreshold = Integer.MAX_VALUE
        controller.count = 0
        def single = countClient.getCountSingle()


        then:"The original exception is thrown"
        single.blockingGet() == 9999

    }

    @Client("/retry-fallback")
    @Retryable(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Fallback
    static class CountClientFallback implements CountService {

        @Override
        int getCount() {
            return 9999
        }

        @Override
        Single<Integer> getCountSingle() {
            return Single.just(9999)
        }
    }

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
