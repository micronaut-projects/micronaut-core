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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link RawHttpClient} with {@link CompletionStage} results instead of reactive streams: it
 * sends "raw" requests with a {@link io.micronaut.http.body.ByteBody} for the request and
 * response body.
 * <p>An instance can be injected like a {@link RawHttpClient}, created with
 * {@link #create(URI)}, or obtained from a {@link RawHttpClient} with
 * {@link RawHttpClient#toAsyncRaw()}.
 * <h2>Cancellation</h2>
 * The stage returned by {@code exchange} is a {@link CompletableFuture}.
 * {@link CompletableFuture#cancel(boolean) Cancelling} it before the response arrives cancels
 * the exchange, like cancelling the subscription to the publisher of a {@link RawHttpClient}
 * exchange: the request is aborted and its body is released, and a response that arrives
 * anyway is closed. Cancelling a stage that is <i>derived</i> from it (e.g. with
 * {@link CompletionStage#thenApply}) only fails the derived stage, it does not cancel the
 * exchange. Once the stage has completed with the response, cancelling it has no effect: close
 * the {@link ByteBodyHttpResponse} to release its body.
 * <h2>Blocking</h2>
 * Unlike {@link RawHttpClient#exchange(HttpRequest, CloseableByteBody, Thread)}, the methods do
 * not take the thread that blocks on the result, which the client uses to detect a deadlock
 * when that thread is one of its own event loop threads. Do not block on the returned stage on
 * such a thread, or use a {@link RawHttpClient} for a blocking exchange.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncRawHttpClient extends Closeable {
    /**
     * Send a raw request, see
     * {@link RawHttpClient#exchange(HttpRequest, CloseableByteBody, Thread)}.
     *
     * @param request     The request metadata (method, URI, headers). The
     *                    {@link HttpRequest#getBody() body} of this object is ignored
     * @param requestBody The request body bytes. {@code null} is equivalent to an empty body. The
     *                    ownership of the body immediately transfers to the client, i.e. the
     *                    client will always call {@link CloseableByteBody#close()} on the body
     *                    even if there is an error before the request is sent
     * @return A {@link CompletableFuture} that completes with the response to this request. This
     * response will <i>usually</i> be a {@link ByteBodyHttpResponse}, unless a filter replaced it
     */
    CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody);

    /**
     * Send a raw request with the given per-exchange options, see
     * {@link RawHttpClient#exchange(HttpRequest, CloseableByteBody, Thread, RawRequestOptions)}.
     *
     * @param request     The request metadata (method, URI, headers). The
     *                    {@link HttpRequest#getBody() body} of this object is ignored, and the
     *                    request is not modified
     * @param requestBody The request body bytes. {@code null} is equivalent to an empty body. The
     *                    ownership of the body immediately transfers to the client, like for
     *                    {@link #exchange(HttpRequest, CloseableByteBody)}
     * @param options     The options for this exchange
     * @return A {@link CompletableFuture} that completes with the response to this request. A
     * response that carries body bytes is a {@link io.micronaut.http.MutableByteBodyHttpResponse},
     * so that it can be changed before it is relayed
     */
    CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, RawRequestOptions options);

    /**
     * Create a new {@link AsyncRawHttpClient}.
     * Note that this method should only be used outside the context of a Micronaut application.
     * The returned {@link AsyncRawHttpClient} is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static AsyncRawHttpClient create(@Nullable URI url) {
        return RawHttpClientFactoryResolver.getFactory().createAsyncRawClient(url);
    }

    /**
     * Create a new {@link AsyncRawHttpClient} with the specified configuration. Note that this
     * method should only be used outside the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url           The base URL
     * @param configuration The client configuration
     * @return The client
     */
    static AsyncRawHttpClient create(@Nullable URI url, HttpClientConfiguration configuration) {
        return RawHttpClientFactoryResolver.getFactory().createAsyncRawClient(url, configuration);
    }
}
