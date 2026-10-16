/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.buffer.Unpooled
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
@Property(name = 'spec.name', value = 'StreamingFilterCompatibilitySpec')
class StreamingFilterCompatibilitySpec extends Specification {
    @Inject @Client('/') StreamingHttpClient client

    void 'exchangeStream accepts a full response substituted by a filter'() {
        expect:
        Flux.from(client.exchangeStream(HttpRequest.GET('/stream-filter/replace')))
            .map { it.body().toString(StandardCharsets.UTF_8) }.collectList().block() == ['replacement']
    }

    void 'eventStream wraps pre-body failures in a client exception'() {
        when:
        Flux.from(client.eventStream(HttpRequest.GET('/stream-filter/error'))).blockLast()

        then:
        def error = thrown(HttpClientException)
        error.cause instanceof IllegalArgumentException
        error.cause.message == 'filter failure'
    }

    @Requires(property = 'spec.name', value = 'StreamingFilterCompatibilitySpec')
    @Filter('/stream-filter/**')
    static class ReplacingFilter implements HttpClientFilter {
        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            if (request.path.endsWith('/error')) {
                return Flux.error(new IllegalArgumentException('filter failure'))
            }
            return Flux.from(chain.proceed(request)).map {
                HttpResponse.ok(Unpooled.copiedBuffer('replacement', StandardCharsets.UTF_8))
            }
        }
    }

    @Requires(property = 'spec.name', value = 'StreamingFilterCompatibilitySpec')
    @Controller('/stream-filter')
    static class Endpoint {
        @Get('/replace') String replace() { 'original' }
    }
}
