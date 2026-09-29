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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.DirectByteBodyAccess;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared implementation of {@link RawRequestOptions} and {@link ProxyHttpClient} for the HTTP
 * client implementations.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RawHttpClientSupport {
    private RawHttpClientSupport() {
    }

    /**
     * Copy the metadata (method, URI, headers and attributes) of a request that is sent with
     * options, so that the options can be applied without changing the given request. Like
     * {@link HttpRequest#toMutableRequest()}, the copy has the attributes of the request, e.g. for
     * the client filters. The body is not copied.
     *
     * @param request The request
     * @param options The options
     * @return The copy
     */
    public static MutableHttpRequest<Object> copyRequest(HttpRequest<?> request, RawRequestOptions options) {
        MutableHttpRequest<Object> copy = HttpRequest.create(request.getMethod(), request.getUri().toString(), request.getMethodName());
        MutableHttpHeaders headers = copy.getHeaders();
        boolean retainHost = options.isRetainHostHeader();
        request.getHeaders().forEach((name, values) -> {
            if (!retainHost && HttpHeaders.HOST.equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                headers.add(name, value);
            }
        });
        copy.getAttributes().putAll(request.getAttributes());
        return copy;
    }

    /**
     * Make a received response mutable.
     *
     * @param response The response
     * @return The mutable response, a {@link MutableByteBodyHttpResponse} if the response
     * carries body bytes
     */
    public static MutableHttpResponse<?> toMutableResponse(HttpResponse<?> response) {
        return response instanceof ByteBodyHttpResponse<?> byteBodyResponse
            ? MutableByteBodyHttpResponse.of(byteBodyResponse)
            : response.toMutableResponse();
    }

    /**
     * Fail the given response flow with a {@link ReadTimeoutException} if it does not complete in
     * time. A response that arrives after the timeout, or after the returned flow was cancelled,
     * is closed. Cancelling the returned flow cancels the given one.
     *
     * @param flow    The response flow
     * @param timeout The timeout, or {@code null} for none
     * @return The flow with the timeout applied
     */
    public static ExecutionFlow<HttpResponse<?>> withResponseTimeout(ExecutionFlow<HttpResponse<?>> flow, @Nullable Duration timeout) {
        CompletableFuture<@Nullable Void> never = new CompletableFuture<>();
        return withResponseTimeout(flow, timeout, never, never);
    }

    /**
     * Fail the given response flow with a {@link ReadTimeoutException} if it does not complete in
     * time. The time from the start of the upload of the request body to its end does not count,
     * so a slow upload does not time out the exchange, while the time to acquire a connection and
     * the wait for the response do. A response that arrives after the timeout, or after the
     * returned flow was cancelled, is closed. Cancelling the returned flow cancels the given one.
     *
     * @param flow          The response flow
     * @param timeout       The timeout, or {@code null} for none
     * @param uploadStarted The stage that completes when the upload of the body starts
     * @param uploaded      The stage that completes when the upload of the body ended
     * @return The flow with the timeout applied
     * @since 5.3.0
     */
    public static ExecutionFlow<HttpResponse<?>> withResponseTimeout(ExecutionFlow<HttpResponse<?>> flow,
                                                                      @Nullable Duration timeout,
                                                                      CompletionStage<?> uploadStarted,
                                                                      CompletionStage<?> uploaded) {
        if (timeout == null) {
            return flow;
        }
        AtomicBoolean done = new AtomicBoolean();
        DelayedExecutionFlow<HttpResponse<?>> result = DelayedExecutionFlow.create();
        ResponseTimer timer = new ResponseTimer(timeout.toNanos(), () -> {
            if (done.compareAndSet(false, true)) {
                result.completeExceptionally(ReadTimeoutException.TIMEOUT_EXCEPTION);
                flow.cancel();
            }
        });
        uploadStarted.whenComplete((ignored, error) -> timer.pause());
        uploaded.whenComplete((ignored, error) -> timer.resume());
        flow.onComplete((response, error) -> {
            timer.stop();
            if (done.compareAndSet(false, true)) {
                result.complete(response, error);
            } else if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                byteBodyResponse.close();
            }
        });
        // forward a cancel from downstream, and close a response that arrives after it
        result.onCancel(() -> {
            timer.stop();
            if (done.compareAndSet(false, true)) {
                flow.cancel();
            }
        });
        return result;
    }

    /**
     * Claim the body bytes of the server request that the given request is, or wraps, e.g. a
     * request a server filter {@link HttpRequest#mutate() mutated} to proxy it. The bytes are only
     * claimed if the body of the given request was not replaced.
     *
     * @param request The request
     * @return The body bytes, or {@code null} if the request is not a server request or its body
     * was replaced
     */
    public static @Nullable CloseableByteBody claimServerRequestBody(HttpRequest<?> request) {
        HttpRequest<?> unwrapped = unwrapUnchangedBody(request);
        if (unwrapped == null) {
            return null;
        }
        if (unwrapped instanceof DirectByteBodyAccess directAccess) {
            // e.g. a request mutated from a Netty server request, which is no wrapper
            // none once its body was set, even when it is also a server request, e.g. the
            // mutable view of a server request
            ByteBody bytes = directAccess.byteBodyDirect();
            return bytes == null ? null : bytes.move();
        }
        if (unwrapped instanceof ServerHttpRequest<?> serverRequest) {
            Object body = request.getBody().orElse(null);
            if (body == null || body == serverRequest.getBody().orElse(null)) {
                return serverRequest.byteBody().move();
            }
        }
        return null;
    }

    /**
     * Unwrap the wrappers around a request down to the request that has the body bytes: a server
     * request, one with direct access to its bytes, or one that is no wrapper.
     *
     * @return The request, or {@code null} if a wrapper replaced the body of the request it wraps
     */
    private static @Nullable HttpRequest<?> unwrapUnchangedBody(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (current instanceof HttpRequestWrapper<?> wrapper
            && !(current instanceof DirectByteBodyAccess)
            && !(current instanceof ServerHttpRequest<?>)) {
            // by identity: a replacement that only compares equal (e.g. redacted) is still a replacement
            if (wrapper.getBody().orElse(null) != wrapper.getDelegate().getBody().orElse(null)) {
                return null;
            }
            current = wrapper.getDelegate();
        }
        return current;
    }

    /**
     * A timeout that can be paused once, for the upload of the request body.
     */
    private static final class ResponseTimer {
        private final Runnable onTimeout;
        private long remainingNanos;
        private long startedAt;
        /**
         * The running phase. Completing it early cancels its scheduled task, so that the task
         * does not keep the flows, and the response, reachable until the timeout elapses.
         */
        private CompletableFuture<@Nullable Void> phase;
        private boolean paused;
        private boolean resumed;
        private boolean stopped;

        ResponseTimer(long timeoutNanos, Runnable onTimeout) {
            this.onTimeout = onTimeout;
            this.remainingNanos = timeoutNanos;
            this.phase = start();
        }

        private CompletableFuture<@Nullable Void> start() {
            startedAt = System.nanoTime();
            CompletableFuture<@Nullable Void> phase = new CompletableFuture<>();
            phase.orTimeout(remainingNanos, TimeUnit.NANOSECONDS).whenComplete((ignored, error) -> {
                if (error instanceof TimeoutException) {
                    onTimeout.run();
                }
            });
            return phase;
        }

        synchronized void pause() {
            if (stopped || paused || resumed) {
                return;
            }
            paused = true;
            remainingNanos -= System.nanoTime() - startedAt;
            phase.complete(null);
            if (remainingNanos <= 0) {
                // it elapsed just before the upload started
                stopped = true;
                onTimeout.run();
            }
        }

        synchronized void resume() {
            if (stopped || !paused || resumed) {
                return;
            }
            resumed = true;
            phase = start();
        }

        synchronized void stop() {
            stopped = true;
            phase.complete(null);
        }
    }
}
