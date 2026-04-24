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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Default {@link AsyncHttpClient} implementation that adapts a reactive {@link HttpClient}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public class DefaultAsyncOverReactiveHttpClient implements AsyncHttpClient {

    private final HttpClient httpClient;

    /**
     * @param httpClient The delegate client
     */
    public DefaultAsyncOverReactiveHttpClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public <I, O, E> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request,
                                                               @Nullable Argument<O> bodyType,
                                                               Argument<E> errorType) {
        return Mono.from(httpClient.exchange(request, bodyType, errorType)).toFuture();
    }

    @Override
    public DefaultAsyncOverReactiveHttpClient start() {
        httpClient.start();
        return this;
    }

    @Override
    public DefaultAsyncOverReactiveHttpClient stop() {
        httpClient.stop();
        return this;
    }

    @Override
    public boolean isRunning() {
        return httpClient.isRunning();
    }

    @Override
    public void close() {
        httpClient.close();
    }

    /**
     * @return The underlying {@link HttpClient}
     */
    protected final HttpClient getHttpClient() {
        return httpClient;
    }
}
