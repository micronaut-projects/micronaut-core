/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientFactory;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Factory to create {@literal java.net.http.*} HTTP Clients.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class JavanetHttpClientFactory implements HttpClientFactory {

    final MediaTypeCodecRegistry mediaTypeCodecRegistry;

    public JavanetHttpClientFactory(@Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    @Override
    @NonNull
    public HttpClient createClient(URL url) {
        return createJavanetClient(url);
    }

    @Override
    @NonNull
    public HttpClient createClient(URL url, @NonNull HttpClientConfiguration configuration) {
        return createJavanetClient(url, configuration);
    }

    private JavanetHttpClient createJavanetClient(URL url) {
        try {
            return createJavanetClient(url != null ? url.toURI() : null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @NonNull
    private JavanetHttpClient createJavanetClient(@Nullable URL url, @NonNull HttpClientConfiguration configuration) {
        try {
            return createJavanetClient(url != null ? url.toURI() : null, configuration);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @NonNull
    private JavanetHttpClient createJavanetClient(@Nullable URI uri) {
        return new JavanetHttpClient(uri);
    }

    @NonNull
    private JavanetHttpClient createJavanetClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        return new JavanetHttpClient(uri, configuration, mediaTypeCodecRegistry);
    }
}
