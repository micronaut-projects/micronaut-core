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
import io.micronaut.http.ServerHttpRequest;
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

import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.ReadableByteChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.BaseStream;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc9110.html">RFC 9110</a>-aware client-side retry
 * filter, opt-in via {@code micronaut.http.client.retry.enabled=true}. Eligibility follows
 * {@link HttpRequestRetryPredicate} and {@link HttpResponseRetryPredicate}; back-off is
 * exponential with jitter, capped by {@link RetryConfiguration#getMaxDelay()} and overridden
 * by {@code Retry-After} when present. Requests with a non-replayable body are bypassed
 * (reactive {@link Publisher} bodies and {@link ServerHttpRequest} raw byte-body wrappers);
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
 * here would result in an {@code IllegalReferenceCountException}.</p>
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
        // Bypass branch invokes continuation.proceed() eagerly — Micronaut filter convention
        // (see clientFilter.adoc / MethodFilter.ReactiveContinuationImpl). Wrapping in
        // Mono.defer would change the publisher shape vs. every other filter and defer the
        // propagated-context pickup that downstream filters expect to be done. The retry
        // branch defers proceed() per attempt inside setUpRetryPipeline.
        return shouldBypassRetry(request)
            ? continuation.proceed()
            : setUpRetryPipeline(request, continuation);
    }

    private boolean shouldBypassRetry(MutableHttpRequest<?> request) {
        return !config.isEnabled()
            || config.getAttempts() <= 1
            || !requestPredicate.isRetryable(request)
            || hasNonReplayableBody(request)
            || Boolean.TRUE.equals(request.getAttributes().get(IN_RETRY_LOOP, Boolean.class).orElse(null));
    }

    private Publisher<HttpResponse<?>> setUpRetryPipeline(MutableHttpRequest<?> request,
                                                          FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
        long retries = (long) config.getAttempts() - 1;
        // Two nested Mono.defers serve different purposes:
        //   - OUTER defer: each subscription of the returned publisher gets its OWN retry
        //     counter (AtomicLong) and sets IN_RETRY_LOOP, paired with doFinally cleanup —
        //     so the request is not mutated unless someone actually subscribes.
        //   - INNER defer: each retryWhen attempt invokes continuation.proceed() afresh,
        //     producing a fresh downstream publisher (not a cached, single-shot result).
        // (No manual ByteBuf release needed — see class Javadoc.)
        return Mono.defer(() -> {
            request.setAttribute(IN_RETRY_LOOP, Boolean.TRUE);
            AtomicLong attempted = new AtomicLong();
            return Mono.defer(() -> Mono.from(continuation.proceed()))
                .retryWhen(buildRetrySpec(retries, attempted));
        }).doFinally(ignored -> request.removeAttribute(IN_RETRY_LOOP, Boolean.class));
    }

    private static boolean hasNonReplayableBody(MutableHttpRequest<?> request) {
        // Framework-level wrapper holding a single-use ByteBody: NettyHttpClient and the JDK
        // client wrap raw client requests as RawHttpRequestWrapper, which implements
        // ServerHttpRequest with a CloseableByteBody that closes after the first attempt.
        // Catching this at the wrapper level (rather than inspecting `getBody()`) avoids
        // mis-classifying future replayable ByteBody implementations.
        if (request instanceof ServerHttpRequest) {
            return true;
        }
        return request.getBody().map(IdempotentRetryClientFilter::isNonReplayableBody).orElse(false);
    }

    /**
     * User-supplied body shapes that are inherently single-pass / single-subscription. A second
     * attempt would observe an exhausted source. Replayable shapes ({@code String}, {@code byte[]},
     * POJOs, {@code Map}, {@code Iterable}, {@code ByteBuffer}, {@code Path}/{@code File}) are
     * intentionally NOT in this list — re-serialization or re-iteration produces the same bytes.
     *
     * <p>Note: this list deliberately does not include
     * {@code io.micronaut.http.body.CloseableByteBody} or {@code ByteBody}. The framework-level
     * wrapper check ({@code instanceof ServerHttpRequest}) handles the case where Micronaut
     * itself uses a single-use {@code ByteBody}; inspecting body content for those types would
     * couple the filter to evolving transport internals and risk false-positives against any
     * future buffered/replayable {@code ByteBody} implementations.</p>
     */
    private static boolean isNonReplayableBody(Object body) {
        return body instanceof Publisher<?>                // reactive stream
            || body instanceof InputStream                 // single-pass byte stream
            || body instanceof Reader                      // single-pass char stream
            || body instanceof ReadableByteChannel         // NIO single-use channel
            || body instanceof Iterator<?>                 // single-pass iterator (Iterable is fine)
            || body instanceof BaseStream<?, ?>;           // java.util.stream.{Stream,IntStream,...}
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
