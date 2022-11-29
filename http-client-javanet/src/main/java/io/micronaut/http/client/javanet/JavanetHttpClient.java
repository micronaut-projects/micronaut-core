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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import org.reactivestreams.Publisher;

import java.net.URI;

/**
 * {@link HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public class JavanetHttpClient implements HttpClient {
    private final URI uri;

    @Nullable
    private final HttpClientConfiguration httpClientConfiguration;

    public JavanetHttpClient(@Nullable URI uri) {
        this(uri, null);
    }

    public JavanetHttpClient(@Nullable URI uri, @Nullable HttpClientConfiguration httpClientConfiguration) {
        this.uri = uri;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new JavanetBlockingHttpClient(uri, httpClientConfiguration);
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(@NonNull HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
