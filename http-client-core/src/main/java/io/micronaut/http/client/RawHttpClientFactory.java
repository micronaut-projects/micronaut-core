/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;

/**
 * Factory for creating {@link RawHttpClient}s without an application context.
 *
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
public interface RawHttpClientFactory {

    /**
     * Create a new {@link RawHttpClient}. Note that this method should only be used outside the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    default RawHttpClient createRawClient(@Nullable URI url) {
        return createRawClient(url, new DefaultHttpClientConfiguration());
    }

    /**
     * Create a new {@link RawHttpClient} with the specified configuration. Note that this method should only be used
     * outside the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url           The base URL
     * @param configuration the client configuration
     * @return The client
     */
    @NonNull
    RawHttpClient createRawClient(@Nullable URI url, @NonNull HttpClientConfiguration configuration);
}
