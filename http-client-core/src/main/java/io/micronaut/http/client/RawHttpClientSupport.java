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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
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
    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
        HttpHeaders.CONNECTION,
        "Keep-Alive",
        HttpHeaders.TE,
        HttpHeaders.TRAILER,
        HttpHeaders.TRANSFER_ENCODING,
        HttpHeaders.UPGRADE
    );
    private static final String PROXY_HEADER_PREFIX = "proxy-";

    private RawHttpClientSupport() {
    }

    /**
     * Remove the hop-by-hop headers: {@code Connection} and the headers it lists,
     * {@code Keep-Alive}, {@code Proxy-*}, {@code TE}, {@code Trailer},
     * {@code Transfer-Encoding} and {@code Upgrade}.
     *
     * @param headers The headers to change
     */
    public static void stripHopByHopHeaders(MutableHttpHeaders headers) {
        for (String connection : headers.getAll(HttpHeaders.CONNECTION)) {
            for (String token : connection.split(",")) {
                String name = token.trim();
                if (!name.isEmpty()) {
                    headers.remove(name);
                }
            }
        }
        for (String name : HOP_BY_HOP_HEADERS) {
            headers.remove(name);
        }
        List<String> proxyHeaders = null;
        for (String name : headers.names()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(PROXY_HEADER_PREFIX)) {
                if (proxyHeaders == null) {
                    proxyHeaders = new ArrayList<>(2);
                }
                proxyHeaders.add(name);
            }
        }
        if (proxyHeaders != null) {
            for (String name : proxyHeaders) {
                headers.remove(name);
            }
        }
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
        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        copy.getAttributes().putAll(request.getAttributes());
        if (!options.isRetainHostHeader()) {
            headers.remove(HttpHeaders.HOST);
        }
        if (options.isStripHopByHopHeaders()) {
            stripHopByHopHeaders(headers);
        }
        return copy;
    }

    /**
     * Apply the options to a received response.
     *
     * @param response The response
     * @param options  The options
     * @return The mutable response, a {@link MutableByteBodyHttpResponse} if the response
     * carries body bytes
     */
    public static MutableHttpResponse<?> toMutableResponse(HttpResponse<?> response, RawRequestOptions options) {
        MutableHttpResponse<?> mutable = response instanceof ByteBodyHttpResponse<?> byteBodyResponse
            ? MutableByteBodyHttpResponse.of(byteBodyResponse)
            : response.toMutableResponse();
        if (options.isStripHopByHopHeaders()) {
            stripHopByHopHeaders(mutable.getHeaders());
        }
        return mutable;
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
        if (timeout == null) {
            return flow;
        }
        AtomicBoolean done = new AtomicBoolean();
        DelayedExecutionFlow<HttpResponse<?>> result = DelayedExecutionFlow.create();
        // completing the timer early cancels its scheduled task, so that the task does not keep
        // the flows, and the response, reachable until the timeout elapses
        CompletableFuture<@Nullable Void> timer = new CompletableFuture<@Nullable Void>()
            .orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
        timer.whenComplete((ignored, error) -> {
            if (error instanceof TimeoutException && done.compareAndSet(false, true)) {
                result.completeExceptionally(ReadTimeoutException.TIMEOUT_EXCEPTION);
                flow.cancel();
            }
        });
        flow.onComplete((response, error) -> {
            timer.complete(null);
            if (done.compareAndSet(false, true)) {
                result.complete(response, error);
            } else if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                byteBodyResponse.close();
            }
        });
        // forward a cancel from downstream, and close a response that arrives after it
        result.onCancel(() -> {
            timer.complete(null);
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
        Object body = request.getBody().orElse(null);
        HttpRequest<?> current = request;
        // a wrapper that replaced the body hides the bytes of the request it wraps
        boolean direct = true;
        while (true) {
            if (current instanceof DirectByteBodyAccess directAccess) {
                // e.g. a request mutated from a Netty server request, which is no wrapper
                ByteBody bytes = direct ? directAccess.byteBodyDirect() : null;
                if (bytes != null || !(current instanceof ServerHttpRequest<?>)) {
                    return bytes == null ? null : bytes.move();
                }
            }
            if (current instanceof ServerHttpRequest<?> serverRequest) {
                if (!direct || body != null && body != serverRequest.getBody().orElse(null)) {
                    return null;
                }
                return serverRequest.byteBody().move();
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                direct &= wrapper.getBody().equals(wrapper.getDelegate().getBody());
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
    }
}
