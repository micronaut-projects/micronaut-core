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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.net.URI;
import java.util.concurrent.CompletionStage;

/**
 * A {@link ProxyHttpClient} whose responses complete a {@link CompletionStage} instead of being
 * emitted by a Reactive Streams publisher.
 *
 * <p>Cancelling the future of a stage (see {@link CompletionStage#toCompletableFuture()}) before
 * the response arrives cancels the exchange; a response that arrives after that is closed.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncProxyHttpClient extends Closeable {

    /**
     * Proxy the given request, see {@link ProxyHttpClient#proxy(HttpRequest)}.
     *
     * @param request The request
     * @return A stage that completes with the response
     */
    default CompletionStage<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    /**
     * Proxy the given request, see {@link ProxyHttpClient#proxy(HttpRequest, ProxyRequestOptions)}.
     *
     * @param request The request
     * @param options Further options for the proxy request
     * @return A stage that completes with the response
     */
    CompletionStage<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options);

    /**
     * Create a new {@link AsyncProxyHttpClient}.
     * Note that this method should only be used outside the context of a Micronaut application.
     * The returned client is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static AsyncProxyHttpClient create(@Nullable URI url) {
        return ProxyHttpClientFactoryResolver.getFactory().createAsyncProxyClient(url);
    }

    /**
     * Create a new {@link AsyncProxyHttpClient} with the specified configuration. Note that this
     * method should only be used outside the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url           The base URL
     * @param configuration The client configuration
     * @return The client
     */
    static AsyncProxyHttpClient create(@Nullable URI url, HttpClientConfiguration configuration) {
        return ProxyHttpClientFactoryResolver.getFactory().createAsyncProxyClient(url, configuration);
    }
}
