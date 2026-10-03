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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Default {@link AsyncRawHttpClient} implementation that adapts a {@link RawHttpClient}.
 * Cancelling the future of an exchange cancels the subscription to the publisher of the
 * {@link RawHttpClient} exchange.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultAsyncOverRawHttpClient implements AsyncRawHttpClient {
    private final RawHttpClient rawHttpClient;

    /**
     * @param rawHttpClient The delegate client
     */
    public DefaultAsyncOverRawHttpClient(RawHttpClient rawHttpClient) {
        this.rawHttpClient = Objects.requireNonNull(rawHttpClient, "rawHttpClient");
    }

    @Override
    public CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody) {
        return RawResponseFuture.of(rawHttpClient.exchange(request, requestBody, null));
    }

    @Override
    public CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, RawRequestOptions options) {
        return RawResponseFuture.of(rawHttpClient.exchange(request, requestBody, null, options));
    }

    @Override
    public void close() throws IOException {
        rawHttpClient.close();
    }
}
