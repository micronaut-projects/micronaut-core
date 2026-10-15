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
import io.micronaut.core.type.Argument
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

/**
 * Verifies that the connection used by a failed streaming call is released when the error body
 * is not consumed, so that a subsequent call is not blocked waiting for the pooled connection.
 */
@Property(name = 'spec.name', value = 'StreamErrorResponseConnectionSpec')
@Property(name = 'micronaut.http.client.pool.max-concurrent-http1-connections', value = '1')
@Property(name = 'micronaut.http.client.read-timeout', value = '30s')
@MicronautTest
class StreamErrorResponseConnectionSpec extends Specification {

    @Inject
    @Client("/")
    StreamingHttpClient client

    @Inject EmbeddedServer server

    void "explicit null error type does not buffer even when configured"() {
        given:
        def configured = HttpClient.create(server.URL, new DefaultHttpClientConfiguration().tap {
            bufferErrorBodyForStreaming = true
        }) as StreamingHttpClient

        when:
        Flux.from(configured.dataStream(HttpRequest.GET('/stream-error/typed'), null)).blockLast()

        then:
        def error = thrown(HttpClientResponseException)
        !error.response.body.isPresent()

        cleanup:
        configured.close()
    }

    void "custom streaming error type is the response body"() {
        when:
        Flux.from(client.dataStream(HttpRequest.GET('/stream-error/typed'), Argument.of(Map))).blockLast()

        then:
        def error = thrown(HttpClientResponseException)
        error.response.body.orElseThrow() == [message: 'bad']
    }

    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void "dataStream error response releases the connection"() {
        when:
        Flux.from(client.dataStream(HttpRequest.GET('/stream-error/fail'))).blockLast()

        then:
        def first = thrown(HttpClientResponseException)
        first.status == HttpStatus.INTERNAL_SERVER_ERROR

        when:
        long start = System.nanoTime()
        Flux.from(client.dataStream(HttpRequest.GET('/stream-error/fail'))).blockLast()

        then:
        def second = thrown(HttpClientResponseException)
        second.status == HttpStatus.INTERNAL_SERVER_ERROR
        second.response.header('X-Custom') == 'custom'
        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5
    }

    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void "exchangeStream error response releases the connection"() {
        when:
        Flux.from(client.exchangeStream(HttpRequest.GET('/stream-error/fail'))).blockFirst()

        then:
        def first = thrown(HttpClientResponseException)
        first.status == HttpStatus.INTERNAL_SERVER_ERROR

        when:
        long start = System.nanoTime()
        Flux.from(client.exchangeStream(HttpRequest.GET('/stream-error/fail'))).blockFirst()

        then:
        def second = thrown(HttpClientResponseException)
        second.status == HttpStatus.INTERNAL_SERVER_ERROR
        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5

        when: "the connection is usable for a successful call afterwards"
        String ok = Flux.from(client.dataStream(HttpRequest.GET('/stream-error/ok')))
            .map { it.toString(java.nio.charset.StandardCharsets.UTF_8) }
            .collectList().block().join('')

        then:
        ok == 'ok'
    }

    @Requires(property = 'spec.name', value = 'StreamErrorResponseConnectionSpec')
    @Controller("/stream-error")
    static class StreamErrorController {

        /**
         * The error body must be larger than what a single socket read delivers, otherwise the
         * connection is released as soon as the full response has been received.
         */
        @Get(uri = "/fail", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<Flux<byte[]>> fail() {
            return HttpResponse.<Flux<byte[]>>serverError()
                .header("X-Custom", "custom")
                .body(Flux.range(0, 2048).map { new byte[8192] })
        }

        @Get(uri = "/typed", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Map<String, String>> typed() {
            HttpResponse.badRequest([message: 'bad'])
        }

        @Get(uri = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok"
        }
    }
}
