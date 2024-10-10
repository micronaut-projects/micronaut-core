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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.net.URI;

/**
 * HTTP client that allows sending "raw" requests with a {@link ByteBody} for the request and
 * response body.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Experimental
public interface RawHttpClient extends Closeable {
    /**
     * Send a raw request.
     *
     * @param request       The request metadata (method, URI, headers). The
     *                      {@link HttpRequest#getBody() body} of this object is ignored
     * @param requestBody   The request body bytes. {@code null} is equivalent to an empty body.
     *                      The ownership of the body immediately transfers to the client, i.e. the
     *                      client will always call {@link CloseableByteBody#close()} on the body
     *                      even if there is an error before the request is sent.
     * @param blockedThread The thread that is blocked waiting for this request. This is used for
     *                      deadlock detection. Optional parameter.
     * @return A mono that will contain the response to this request. This response will
     * <i>usually</i> be a {@link ByteBodyHttpResponse}, unless a filter replaced it.
     */
    @NonNull
    @SingleResult
    Publisher<? extends HttpResponse<?>> exchange(@NonNull HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread);

    /**
     * Create a new {@link RawHttpClient}.
     * Note that this method should only be used outside the context of a Micronaut application.
     * The returned {@link RawHttpClient} is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static RawHttpClient create(@Nullable URI url) {
        return RawHttpClientFactoryResolver.getFactory().createRawClient(url);
    }

    /**
     * Create a new {@link RawHttpClient} with the specified configuration. Note that this method should only be used
     * outside the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url           The base URL
     * @param configuration the client configuration
     * @return The client
     */
    static RawHttpClient create(@Nullable URI url, @NonNull HttpClientConfiguration configuration) {
        return RawHttpClientFactoryResolver.getFactory().createRawClient(url, configuration);
    }
}
