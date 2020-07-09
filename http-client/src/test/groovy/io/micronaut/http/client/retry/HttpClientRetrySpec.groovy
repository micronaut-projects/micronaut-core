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

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.reactivex.Single
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpClientRetrySpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
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
        controller.countThreshold = 10
        controller.count = 0
        countClient.getCount()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
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
        controller.countThreshold = 10
        controller.count = 0
        def single = countClient.getCountSingle()
        single.blockingGet()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"

    }

    void "test retry JSON post"() {
        given:
        RetryableClient client = context.getBean(RetryableClient)
        def result = client.post(new FooDTO(foo: "Good")).blockingGet()

        expect:
        result == 'Good'
    }

    @Client("/retry-test")
    @Retryable(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Controller("/retry-test")
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

    @Controller("/retry-test/json")
    static class JsonController {
        boolean first = true
        @Post("/foo")
        Single<String> post(@Body @NonNull FooDTO foo) {
            if (first) {
                first = false
                return Single.error(new RuntimeException("First request failed"))
            } else {
                Single.just(foo.foo)
            }
        }
    }

    static interface CountService {

        @Get('/count')
        int getCount()

        @Get('/rx-count')
        Single<Integer> getCountSingle()
    }

    @Client("/retry-test/json")
    @Retryable(delay = "10ms", attempts = "2", maxDelay = "1s")
    static interface RetryableClient {

        @Post("/foo")
        Single<String> post(@Body @NonNull FooDTO foo);
    }

    static class FooDTO {
        String foo
    }
}
