/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * A factory to create Streaming HTTP clients.
 *
 * @author James Kleeh
 * @author Sergio del Amo
 * @since 3.0.0
 */
public interface StreamingHttpClientFactory {

    /**
     * Create a new {@link StreamingHttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     * @deprecated Use {@link #createStreamingClient(URI)} instead
     */
    @NonNull
    @Deprecated
    default StreamingHttpClient createStreamingClient(@Nullable URL url) {
        try {
            return createStreamingClient(url != null ? url.toURI() : null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create a new {@link StreamingHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     * @since 2.2.0
     * @deprecated Use {@link #createStreamingClient(URI, HttpClientConfiguration)} instead
     */
    @NonNull
    @Deprecated
    default StreamingHttpClient createStreamingClient(@Nullable URL url, @NonNull HttpClientConfiguration configuration) {
        try {
            return createStreamingClient(url != null ? url.toURI() : null, configuration);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create a new {@link StreamingHttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param uri The base URI
     * @return The client
     */
    @NonNull
    default StreamingHttpClient createStreamingClient(@Nullable URI uri) {
        try {
            return createStreamingClient(uri != null ? uri.toURL() : null);
        } catch (MalformedURLException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Create a new {@link StreamingHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param uri The base URI
     * @param configuration The client configuration
     * @return The client
     * @since 2.2.0
     */
    @NonNull
    default StreamingHttpClient createStreamingClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        try {
            return createStreamingClient(uri != null ? uri.toURL() : null, configuration);
        } catch (MalformedURLException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
