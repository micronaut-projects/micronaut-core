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
package io.micronaut.http.client.retry;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration.RetryConfiguration;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.FilterContinuation;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc9110.html">RFC 9110</a>-aware client-side retry
 * filter, opt-in via {@code micronaut.http.client.retry.enabled=true}. Eligibility follows
 * {@link HttpRequestRetryPredicate} and {@link HttpResponseRetryPredicate}; back-off is
 * exponential with jitter, capped by {@link RetryConfiguration#getMaxDelay()} and overridden
 * by {@code Retry-After} when present. Streamed-body requests are bypassed (no replay);
 * exhausted retries propagate the final throwable.
 *
 * <p><b>Implementation Note regarding Netty ByteBufs:</b></p>
 * <p>Unlike raw Reactor Netty or Spring WebClient, where an unconsumed 5xx body publisher
 * will hold the connection open and exhaust the pool, this filter does not manually drain
 * or release the response body on retry. Micronaut's {@code NettyHttpClient} materializes
 * the error body and explicitly releases the underlying {@code ByteBuf} in a {@code finally}
 * block before propagating the {@link HttpClientResponseException}. By the time the failure
 * reaches this filter, the response carries a decoded Java body, the buffer is already
 * released, and the connection is back in the pool. Adding a manual {@code release()} call
 * here would result in an {@link io.netty.util.IllegalReferenceCountException}.</p>
 *
 *
 * @since 5.0.0
 */
@Internal
@ClientFilter
@Requires(property = DefaultHttpClientConfiguration.PREFIX + "." + RetryConfiguration.PREFIX + ".enabled", value = "true")
public final class IdempotentRetryClientFilter implements Ordered {

    /**
     * Calling continuation.proceed() inside a reactive retry operator re-runs the
     * filter chain (including this filter). To prevent infinite re-entry loops and
     * ensure the outermost invocation owns the retry loop (and tracks attempts correctly),
     * we mark the request with this attribute on first entry.
     */
    private static final CharSequence IN_RETRY_LOOP = "io.micronaut.http.client.retry.in_loop";

    private final RetryConfiguration config;
    private final HttpRequestRetryPredicate requestPredicate;
    private final HttpResponseRetryPredicate responsePredicate;
    private final Clock clock;

    public IdempotentRetryClientFilter(DefaultHttpClientConfiguration clientConfiguration,
                                       HttpRequestRetryPredicate requestPredicate,
                                       HttpResponseRetryPredicate responsePredicate) {
        this(clientConfiguration.getRetryConfiguration(), requestPredicate, responsePredicate, Clock.systemUTC());
    }

    IdempotentRetryClientFilter(RetryConfiguration config,
                                HttpRequestRetryPredicate requestPredicate,
                                HttpResponseRetryPredicate responsePredicate,
                                Clock clock) {
        this.config = config;
        this.requestPredicate = requestPredicate;
        this.responsePredicate = responsePredicate;
        this.clock = clock;
    }

    /**
     * Outermost in the request direction (low order value = high precedence). Each retry
     * triggers the entire downstream chain again, so auth filters re-issue fresh tokens and
     * tracing filters create new spans per attempt.
     *
     * @return The filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @RequestFilter
    public Publisher<HttpResponse<?>> filter(MutableHttpRequest<?> request,
                                             FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
        if (!config.isEnabled() || config.getAttempts() <= 1
            || !requestPredicate.isRetryable(request)
            || hasStreamedBody(request)) {
            return continuation.proceed();
        }
        // Skip re-entries from upstream reactive retries
        if (Boolean.TRUE.equals(request.getAttributes().get(IN_RETRY_LOOP, Boolean.class).orElse(null))) {
            return continuation.proceed();
        }
        request.setAttribute(IN_RETRY_LOOP, Boolean.TRUE);
        long retries = (long) config.getAttempts() - 1;
        AtomicLong attempted = new AtomicLong();
        // Note: Do not manually release or drain the response body here.
        // Micronaut's NettyHttpClient safely releases the underlying ByteBuf before
        // propagating the exception. See class Javadoc for implementation details.
        return Mono.defer(() -> Mono.from(continuation.proceed()))
            .retryWhen(buildRetrySpec(retries, attempted));
    }

    private static boolean hasStreamedBody(MutableHttpRequest<?> request) {
        return request.getBody().filter(b -> b instanceof Publisher<?>).isPresent();
    }

    private Retry buildRetrySpec(long retries, AtomicLong attempted) {
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = signal.failure();
            long n = attempted.getAndIncrement();
            if (n >= retries || !responsePredicate.shouldRetry(failure)) {
                return Mono.error(failure);
            }
            Duration delay = computeDelay(n, failure);
            return delay.isZero() ? Mono.just(n) : Mono.delay(delay).map(i -> n);
        }));
    }

    private Duration computeDelay(long retryIndex, Throwable failure) {
        Duration retryAfter = retryAfterFromFailure(failure);
        if (retryAfter != null) {
            return cap(retryAfter, config.getMaxDelay());
        }
        double base = config.getDelay().toMillis() * Math.pow(config.getMultiplier(), retryIndex);
        double jitter = config.getJitter();
        if (jitter > 0) {
            double random = ThreadLocalRandom.current().nextDouble(-jitter, jitter);
            base = base * (1.0 + random);
        }
        long millis = (long) Math.max(0, base);
        Duration delay = Duration.ofMillis(millis);
        return cap(delay, config.getMaxDelay());
    }

    @Nullable
    private Duration retryAfterFromFailure(Throwable failure) {
        if (!config.isRespectRetryAfter() || !(failure instanceof HttpClientResponseException ex)) {
            return null;
        }
        // Retry-After is canonically associated with 503 by RFC 9110 §10.2.3
        // (https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after) and with 429 by
        // RFC 6585 §4 (https://www.rfc-editor.org/rfc/rfc6585.html#section-4). RFC 9110
        // §10.2.3 also lists 3xx (handled by redirect-following, not retry). Other statuses
        // may carry it but are not honored here — replace HttpResponseRetryPredicate to broaden.
        HttpStatus status = ex.getStatus();
        if (status != HttpStatus.TOO_MANY_REQUESTS && status != HttpStatus.SERVICE_UNAVAILABLE) {
            return null;
        }
        String header = ex.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER);
        return RetryAfterParser.parse(header, clock);
    }

    private static Duration cap(Duration value, Duration max) {
        return value.compareTo(max) > 0 ? max : value;
    }
}
