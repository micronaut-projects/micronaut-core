/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Abstract class implementation of {@link HttpClientFactory}.
 *
 * @param <T> The type of {@link HttpClient} created by this factory
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public abstract class AbstractHttpClientFactory<T extends HttpClient> implements HttpClientFactory {

    protected final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    protected final ConversionService conversionService;

    protected AbstractHttpClientFactory(@Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
                                        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                                        ConversionService conversionService) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
        this.conversionService = conversionService;
    }

    /**
     * Creates a new {@link HttpClient} instance for a given URI.
     * @param uri The URI
     * @return The client
     */
    @NonNull
    protected abstract T createHttpClient(@Nullable URI uri);

    /**
     * Creates a new {@link HttpClient} instance for a given URI and configuration.
     * @param uri The URI
     * @param configuration The configuration
     * @return The client
     */
    @NonNull
    protected abstract T createHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration);

    @Override
    @NonNull
    public HttpClient createClient(URL url) {
        return createHttpClient(url);
    }

    @Override
    @NonNull
    public HttpClient createClient(URL url, @NonNull HttpClientConfiguration configuration) {
        return createHttpClient(url, configuration);
    }

    private T createHttpClient(URL url) {
        try {
            return createHttpClient(url != null ? url.toURI() : null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @NonNull
    private T createHttpClient(@Nullable URL url, @NonNull HttpClientConfiguration configuration) {
        try {
            return createHttpClient(url != null ? url.toURI() : null, configuration);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
