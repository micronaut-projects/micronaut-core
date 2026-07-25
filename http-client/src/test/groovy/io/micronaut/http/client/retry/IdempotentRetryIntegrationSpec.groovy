/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class IdempotentRetryIntegrationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name'                                  : 'IdempotentRetryIntegrationSpec',
        'micronaut.http.client.retry.enabled'        : 'true',
        'micronaut.http.client.retry.attempts'       : '4',
        'micronaut.http.client.retry.delay'          : '1ms',
        'micronaut.http.client.retry.max-delay'      : '10ms',
        'micronaut.http.client.retry.multiplier'     : '1.0',
        'micronaut.http.client.retry.jitter'         : '0.0'
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "GET retries on 503 from server until success"() {
        given:
        def controller = server.applicationContext.getBean(FlakyController)
        controller.reset(3)

        when:
        def response = client.toBlocking().exchange('/retry-it/get', String)

        then:
        response.status == HttpStatus.OK
        controller.attempts.get() == 3
    }

    void "GET exhausts retries and propagates the original exception"() {
        given:
        def controller = server.applicationContext.getBean(FlakyController)
        controller.reset(99)

        when:
        client.toBlocking().exchange('/retry-it/get', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.SERVICE_UNAVAILABLE
        controller.attempts.get() == 4
    }

    void "POST is not retried even on 503"() {
        given:
        def controller = server.applicationContext.getBean(FlakyController)
        controller.reset(99)

        when:
        client.toBlocking().exchange(HttpRequest.POST('/retry-it/post', 'body'), String)

        then:
        thrown(HttpClientResponseException)
        controller.attempts.get() == 1
    }

    @Requires(property = 'spec.name', value = 'IdempotentRetryIntegrationSpec')
    @Controller('/retry-it')
    static class FlakyController {
        AtomicInteger attempts = new AtomicInteger()
        int succeedAt = 1

        void reset(int succeedAt) {
            this.succeedAt = succeedAt
            attempts.set(0)
        }

        @Get('/get')
        HttpResponse<?> get() {
            int n = attempts.incrementAndGet()
            return n >= succeedAt ? HttpResponse.ok('done') : HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        }

        @Post('/post')
        HttpResponse<?> post(@Body String body) {
            int n = attempts.incrementAndGet()
            return n >= succeedAt ? HttpResponse.ok('done') : HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }
}
