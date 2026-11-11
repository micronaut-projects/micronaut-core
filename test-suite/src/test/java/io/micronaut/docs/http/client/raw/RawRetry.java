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
package io.micronaut.docs.http.client.raw;

// tag::imports[]
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
// end::imports[]

// tag::class[]
public class RawRetry {
    private static final RawRequestOptions OPTIONS = RawRequestOptions.proxy().toBuilder()
        .readIdleTimeout(Duration.ofMinutes(5)) // <1>
        .returnUnsentBody(true) // <2>
        .build();

    private final AsyncRawHttpClient primary;
    private final AsyncRawHttpClient fallback;

    public RawRetry(AsyncRawHttpClient primary, AsyncRawHttpClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, CloseableByteBody body) {
        return primary.exchange(request, body, OPTIONS)
            .handle((response, error) -> {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof UnprocessedRequestException unprocessed) {
                    Optional<CloseableByteBody> unsent = unprocessed.takeUnsentBody(); // <3>
                    if (unsent.isPresent()) {
                        return fallback.exchange(request, unsent.get(), OPTIONS); // <4>
                    }
                }
                return error == null ? CompletableFuture.<HttpResponse<?>>completedFuture(response) : CompletableFuture.<HttpResponse<?>>failedFuture(cause);
            })
            .thenCompose(stage -> stage);
    }
}
// end::class[]
