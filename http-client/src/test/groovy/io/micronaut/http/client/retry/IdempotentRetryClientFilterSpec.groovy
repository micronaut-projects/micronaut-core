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

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.*
import io.micronaut.http.body.ByteBody
import io.micronaut.http.client.HttpClientConfiguration.RetryConfiguration
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.FilterContinuation
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.channels.Channels
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import java.util.stream.Stream

class IdempotentRetryClientFilterSpec extends Specification {

    // Mirrors the private constant in IdempotentRetryClientFilter; kept here as a literal so
    // the test exercises the actual stored attribute name without enlarging public API.
    private static final String IN_RETRY_LOOP_KEY = 'io.micronaut.http.client.retry.in_loop'

    void "non-idempotent POST is not retried on 503"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 3)

        when:
        Mono.from(filter.filter(HttpRequest.POST('/orders', '{}'), continuation)).block()

        then:
        thrown(HttpClientResponseException)
        continuation.calls.get() == 1
    }

    void "POST with Idempotency-Key header is retried on 503 and succeeds"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.SERVICE_UNAVAILABLE) },
            { Mono.just(HttpResponse.ok('done')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)
        def request = HttpRequest.POST('/payments', '{}').header('Idempotency-Key', 'uuid-1')

        when:
        def response = Mono.from(filter.filter(request, continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "GET 404 is not retried (terminal 4xx)"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.NOT_FOUND) }
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when:
        Mono.from(filter.filter(HttpRequest.GET('/missing'), continuation)).block()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND
        continuation.calls.get() == 1
    }

    void "GET 502 is retried up to configured attempts and succeeds"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.BAD_GATEWAY) },
            { responseException(HttpStatus.BAD_GATEWAY) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/status'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 3
    }

    void "exhaustion propagates the final exception"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 2, delay: Duration.ZERO)

        when:
        Mono.from(filter.filter(HttpRequest.GET('/flaky'), continuation)).block()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.SERVICE_UNAVAILABLE
        continuation.calls.get() == 2
    }

    void "transport errors are retried"() {
        given:
        def continuation = scriptedContinuation([
            { Mono.error(new HttpClientException('connect timeout')) as Publisher },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/x'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "Retry-After header (delta-seconds form) is honored on 503"() {
        given:
        def headers = ['Retry-After': '0']
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.SERVICE_UNAVAILABLE, headers) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ofSeconds(60))

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/rate'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "Retry-After HTTP-date form is honored on 503 with a fixed clock"() {
        given:
        def fixedNow = Instant.parse('2026-04-28T12:00:00Z')
        def clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
        // Header at the same instant → delta of zero
        def headers = ['Retry-After': 'Tue, 28 Apr 2026 12:00:00 GMT']
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.SERVICE_UNAVAILABLE, headers) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ofSeconds(60), clock: clock)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/rate'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "Retry-After HTTP-date in the past coerces to zero delay"() {
        given:
        def fixedNow = Instant.parse('2026-04-28T12:00:00Z')
        def clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
        // Header date is one hour in the past → would be a negative delta; parser coerces to ZERO
        def headers = ['Retry-After': 'Tue, 28 Apr 2026 11:00:00 GMT']
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.SERVICE_UNAVAILABLE, headers) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ofSeconds(60), clock: clock)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/rate'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "GET 408 Request Timeout is retried"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.REQUEST_TIMEOUT) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/slow'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "GET 429 (no Retry-After header) is retried"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.TOO_MANY_REQUESTS) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/rate'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "respectRetryAfter=false ignores the header (would otherwise block for minutes)"() {
        given:
        // Retry-After: 600 would block ~10 minutes if honored. With respectRetryAfter=false the
        // configured delay (Duration.ZERO) is used instead, so the test completes in ms.
        def headers = ['Retry-After': '600']
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.SERVICE_UNAVAILABLE, headers) },
            { Mono.just(HttpResponse.ok('ok')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO, respectRetryAfter: false)

        when:
        def response = Mono.from(filter.filter(HttpRequest.GET('/rate'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "streamed body request is bypassed (single attempt)"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 5, delay: Duration.ZERO)
        def streamed = HttpRequest.PUT('/upload', Mono.just('chunk') as Publisher)

        when:
        Mono.from(filter.filter(streamed, continuation)).block()

        then:
        thrown(HttpClientResponseException)
        continuation.calls.get() == 1
    }

    @Unroll
    void "non-replayable body shape is bypassed: #desc"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 5, delay: Duration.ZERO)
        def request = HttpRequest.PUT('/upload', body)

        when:
        Mono.from(filter.filter(request, continuation)).block()

        then:
        thrown(HttpClientResponseException)
        continuation.calls.get() == 1   // single attempt; retry bypassed

        where:
        desc                | body
        'InputStream'       | new ByteArrayInputStream('payload'.bytes)
        'Reader'            | new StringReader('payload')
        'ReadableByteChannel' | Channels.newChannel(new ByteArrayInputStream('payload'.bytes))
        'Iterator<?>'       | ['a', 'b', 'c'].iterator()
        'Stream<?>'         | Stream.of('a', 'b', 'c')
        'IntStream'         | IntStream.range(0, 5)
    }

    void "replayable body (String) IS retried — confirms we don't over-block"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.BAD_GATEWAY) },
            { Mono.just(HttpResponse.ok('done')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)

        when: 'PUT (idempotent) with a String body — replayable, should retry'
        def response = Mono.from(filter.filter(HttpRequest.PUT('/upload', 'hello'), continuation)).block()

        then:
        response.status == HttpStatus.OK
        continuation.calls.get() == 2
    }

    void "ServerHttpRequest (raw byte-body wrapper) is bypassed (single attempt)"() {
        given: 'simulates the RawHttpRequestWrapper Netty/JDK clients use for raw requests'
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 5, delay: Duration.ZERO)
        def rawRequest = new FakeRawRequest(HttpRequest.GET('/x'))

        when:
        Mono.from(filter.filter(rawRequest, continuation)).block()

        then:
        thrown(HttpClientResponseException)
        continuation.calls.get() == 1
    }

    void "filter() does NOT mutate the request when the returned publisher is never subscribed"() {
        given: 'a continuation that would record any proceed() call'
        def continuation = countingContinuation { Mono.just(HttpResponse.ok('done')) as Publisher }
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)
        def request = HttpRequest.GET('/x')

        when: 'the filter is invoked but the resulting publisher is discarded without subscription'
        filter.filter(request, continuation)

        then: 'no IN_RETRY_LOOP attribute set; no proceed() invoked'
        !request.getAttributes().get(IN_RETRY_LOOP_KEY, Boolean.class).isPresent()
        continuation.calls.get() == 0
    }

    void "IN_RETRY_LOOP request attribute is cleared after successful completion"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.BAD_GATEWAY) },
            { Mono.just(HttpResponse.ok('done')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)
        def request = HttpRequest.GET('/x')

        when:
        Mono.from(filter.filter(request, continuation)).block()

        then: 'doFinally cleared the guard attribute'
        !request.getAttributes().get(IN_RETRY_LOOP_KEY, Boolean.class).isPresent()
    }

    void "IN_RETRY_LOOP request attribute is cleared after exhaustion (error terminal)"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(attempts: 2, delay: Duration.ZERO)
        def request = HttpRequest.GET('/x')

        when:
        Mono.from(filter.filter(request, continuation)).block()

        then:
        thrown(HttpClientResponseException)

        and: 'doFinally cleared the guard attribute even on the error terminal signal'
        !request.getAttributes().get(IN_RETRY_LOOP_KEY, Boolean.class).isPresent()
    }

    void "retry counter is per-subscription, not shared across multiple subscribes of the same returned publisher"() {
        given: 'four scripted responses — one fail+success per subscription'
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.BAD_GATEWAY) },                  // sub 1, attempt 1
            { Mono.just(HttpResponse.ok('first')) as Publisher },           // sub 1, attempt 2 — success
            { responseException(HttpStatus.BAD_GATEWAY) },                  // sub 2, attempt 1
            { Mono.just(HttpResponse.ok('second')) as Publisher }           // sub 2, attempt 2 — success
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)
        def request = HttpRequest.GET('/x')
        def publisher = filter.filter(request, continuation)

        when: 'first subscription burns 1 retry'
        def first = Mono.from(publisher).block()

        then:
        first.status == HttpStatus.OK
        continuation.calls.get() == 2

        when: 'second subscription on the SAME publisher must retry independently'
        def second = Mono.from(publisher).block()

        then: 'a shared retry counter would have been at 1 already and prevented the retry'
        second.status == HttpStatus.OK
        continuation.calls.get() == 4
    }

    void "IN_RETRY_LOOP attribute is cleared after the call so request reuse works"() {
        given:
        def continuation = scriptedContinuation([
            { responseException(HttpStatus.BAD_GATEWAY) },
            { Mono.just(HttpResponse.ok('first')) as Publisher },
            { responseException(HttpStatus.BAD_GATEWAY) },
            { Mono.just(HttpResponse.ok('second')) as Publisher }
        ])
        def filter = newFilter(attempts: 3, delay: Duration.ZERO)
        def request = HttpRequest.GET('/x')

        when: 'first call retries once and succeeds'
        def first = Mono.from(filter.filter(request, continuation)).block()

        then:
        first.status == HttpStatus.OK
        continuation.calls.get() == 2

        when: 'reuse the same request — without cleanup the second call would bypass retry'
        def second = Mono.from(filter.filter(request, continuation)).block()

        then:
        second.status == HttpStatus.OK
        continuation.calls.get() == 4   // two more attempts; retry engaged on the second call
    }

    void "disabled config is a pass-through"() {
        given:
        def continuation = countingContinuation { responseException(HttpStatus.SERVICE_UNAVAILABLE) }
        def filter = newFilter(enabled: false, attempts: 5)

        when:
        Mono.from(filter.filter(HttpRequest.GET('/x'), continuation)).block()

        then:
        thrown(HttpClientResponseException)
        continuation.calls.get() == 1
    }



    private static IdempotentRetryClientFilter newFilter(Map opts) {
        def cfg = new RetryConfiguration()
        cfg.enabled = opts.getOrDefault('enabled', true)
        cfg.attempts = (int) opts.getOrDefault('attempts', 3)
        cfg.delay = (Duration) opts.getOrDefault('delay', Duration.ofMillis(1))
        cfg.maxDelay = (Duration) opts.getOrDefault('maxDelay', Duration.ofSeconds(10))
        cfg.multiplier = (double) opts.getOrDefault('multiplier', 1.0)
        cfg.jitter = (double) opts.getOrDefault('jitter', 0.0)
        cfg.respectRetryAfter = (boolean) opts.getOrDefault('respectRetryAfter', true)
        Clock clock = (Clock) opts.getOrDefault('clock', Clock.systemUTC())
        new IdempotentRetryClientFilter(
            cfg,
            HttpRequestRetryPredicate.rfc9110(),
            HttpResponseRetryPredicate.rfc9110(),
            clock
        )
    }

    private static CountingContinuation countingContinuation(Closure<Publisher<HttpResponse<?>>> response) {
        new CountingContinuation(scripted: [response], cycle: true)
    }

    private static CountingContinuation scriptedContinuation(List<Closure<Publisher<HttpResponse<?>>>> scripted) {
        new CountingContinuation(scripted: scripted, cycle: false)
    }

    private static Publisher<HttpResponse<?>> responseException(HttpStatus status, Map<String, String> headers = [:]) {
        def response = HttpResponse.status(status)
        headers.each { k, v -> response.header(k, v) }
        Mono.error(new HttpClientResponseException(status.reason, response))
    }

    /**
     * Mirrors the shape of NettyHttpClient/JdkHttpClient {@code RawHttpRequestWrapper} —
     * a {@link MutableHttpRequest} that ALSO implements {@link ServerHttpRequest}, holding
     * a {@link ByteBody} that would be consumed/closed on the first attempt. The retry
     * filter must bypass these to avoid second-attempt failures on a closed body.
     */
    private static class FakeRawRequest<B> extends MutableHttpRequestWrapper<B> implements ServerHttpRequest<B> {
        FakeRawRequest(MutableHttpRequest<B> delegate) {
            super(ConversionService.SHARED, delegate)
        }

        @Override
        ByteBody byteBody() {
            return null   // unused; the filter inspects the type, not the body contents
        }
    }

    private static class CountingContinuation implements FilterContinuation<Publisher<HttpResponse<?>>> {
        AtomicInteger calls = new AtomicInteger()
        List<Closure<Publisher<HttpResponse<?>>>> scripted
        boolean cycle

        @Override
        FilterContinuation<Publisher<HttpResponse<?>>> request(HttpRequest<?> request) {
            return this
        }

        @Override
        Publisher<HttpResponse<?>> proceed() {
            int idx = calls.getAndIncrement()
            int slot = cycle ? 0 : idx
            if (slot >= scripted.size()) {
                throw new IllegalStateException("Continuation invoked more times than scripted (${idx + 1})")
            }
            return scripted[slot].call()
        }
    }
}
