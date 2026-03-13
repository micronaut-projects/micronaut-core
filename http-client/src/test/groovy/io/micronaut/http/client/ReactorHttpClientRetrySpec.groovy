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
package io.micronaut.http.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

@Property(name = 'spec.name', value = 'ReactorHttpClientRetrySpec')
@MicronautTest
class ReactorHttpClientRetrySpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Inject
    RetryController retryController

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/12407')
    void 'reactor retryWhen resubscribes and sends a new request after an HTTP error'() {
        given:
        retryController.reset(3)

        when:
        HttpResponse<String> response = Mono.from(client.exchange(HttpRequest.GET('/reactor-retry'), String))
            .retryWhen(Retry.maxInARow(5))
            .block()

        then:
        response.status() == HttpStatus.OK
        response.body() == 'ok'
        retryController.requestCount.get() == 3
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/12407')
    void 'reactor retryWhen keeps resending until retries are exhausted'() {
        given:
        retryController.reset(10)

        when:
        Mono.from(client.exchange(HttpRequest.GET('/reactor-retry'), String))
            .retryWhen(Retry.maxInARow(5))
            .block()

        then:
        Exception e = thrown()
        e.class.name == 'reactor.core.Exceptions$RetryExhaustedException'
        e.cause instanceof HttpClientResponseException
        ((HttpClientResponseException) e.cause).status == HttpStatus.INTERNAL_SERVER_ERROR
        retryController.requestCount.get() == 6
    }

    @Requires(property = 'spec.name', value = 'ReactorHttpClientRetrySpec')
    @Controller('/reactor-retry')
    static class RetryController {
        final AtomicInteger requestCount = new AtomicInteger()
        volatile int successfulAttempt = 3

        void reset(int successfulAttempt) {
            this.successfulAttempt = successfulAttempt
            requestCount.set(0)
        }

        @Get
        HttpResponse<String> get() {
            int attempt = requestCount.incrementAndGet()
            if (attempt < successfulAttempt) {
                return HttpResponse.serverError('attempt ' + attempt)
            }
            return HttpResponse.ok('ok')
        }
    }
}
