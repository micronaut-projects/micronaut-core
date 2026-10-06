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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.stream.NoTrailers;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * What {@link DirectRouteLookup#find(DirectRequest, io.micronaut.http.HttpResponseFactory)}
 * returns when the direct route that matches a request is asynchronous: it runs on an executor,
 * or completes its response later. The route was matched once and has started: the
 * {@link #stage() stage} completes with the response to write, or with {@code null} if the route
 * declined the request.
 *
 * <p>It is not a response to write: a server runtime checks for it with {@code instanceof}
 * before it writes the response {@code find} returned. Its status, {@code 102}, has no headers
 * and no body, and its attributes are immutable.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class PendingResponse implements HttpResponse<Object> {

    private static final MutableConvertibleValues<Object> NO_ATTRIBUTES = new MutableConvertibleValuesMap<>(Map.of());

    private final CompletionStage<@Nullable HttpResponse<?>> stage;

    /**
     * @param stage The stage of the response of the route, completed with {@code null} if the
     *              route declines the request
     */
    public PendingResponse(CompletionStage<@Nullable HttpResponse<?>> stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    /**
     * The stage of the response of the route. It completes on whatever thread completes the
     * response, e.g. the executor of the route, with a {@code 500} response if the function of
     * the route throws or its stage fails, and with {@code null} if the route declines the
     * request. Cancelling it cancels the stage of the route, and a function that has not started
     * on its executor does not run.
     *
     * @return The stage of the response
     */
    public CompletionStage<@Nullable HttpResponse<?>> stage() {
        return stage;
    }

    @Override
    public int code() {
        return HttpStatus.PROCESSING.getCode();
    }

    @Override
    public String reason() {
        return "Pending direct route";
    }

    @Override
    public HttpHeaders getHeaders() {
        return NoTrailers.HEADERS;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        // Map.of: immutable
        return NO_ATTRIBUTES;
    }

    @Override
    public Optional<Object> getBody() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "PendingResponse[" + stage + "]";
    }
}
