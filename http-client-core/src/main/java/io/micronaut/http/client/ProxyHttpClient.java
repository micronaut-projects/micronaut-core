/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

import java.net.URL;

/**
 * Interface that allows proxying of HTTP requests in controllers and filters.
 *
 * @author graemerocher
 * @since 2.0.0
 */
public interface ProxyHttpClient {

    /**
     * Proxy the given request and emit the response. This method expects the full absolute URL to be included in the request.
     * If a relative URL is specified then the method will try to resolve the URI for the current server otherwise an exception will be thrown.
     *
     * @param request The request
     * @return A publisher that emits the response.
     */
    Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request);

    /**
     * Create a new {@link ProxyHttpClient}.
     * Note that this method should only be used outside of the context of a Micronaut application.
     * The returned {@link ProxyHttpClient} is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static ProxyHttpClient create(@Nullable URL url) {
        return ProxyHttpClientFactoryResolver.getFactory().createProxyClient(url);
    }

    /**
     * Create a new {@link ProxyHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static ProxyHttpClient create(@Nullable URL url, HttpClientConfiguration configuration) {
        return ProxyHttpClientFactoryResolver.getFactory().createProxyClient(url, configuration);
    }
}
