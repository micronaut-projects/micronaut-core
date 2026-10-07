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
import io.micronaut.http.MutableHttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The {@link AsyncProxyHttpClient} of a {@link ProxyHttpClient} that only has the reactive
 * {@code proxy} methods: the response the publisher emits completes the stage.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class AsyncOverReactiveProxyHttpClient implements AsyncProxyHttpClient {

    private final ProxyHttpClient client;

    /**
     * @param client The reactive client
     */
    AsyncOverReactiveProxyHttpClient(ProxyHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletionStage<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options) {
        return RawResponseFuture.ofMutable(client.proxy(request, options));
    }

    @Override
    public void close() throws IOException {
        // a ProxyHttpClient is not closeable itself, the clients that implement it are
        if (client instanceof Closeable closeable) {
            closeable.close();
        } else if (client instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
