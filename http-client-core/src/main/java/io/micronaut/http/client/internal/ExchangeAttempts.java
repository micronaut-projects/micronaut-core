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
package io.micronaut.http.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.multipart.MultipartBody;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.function.Predicate;

/**
 * Helpers of the clients for the attempts of an exchange with {@link RetryingExchange retry}.
 *
 * @since 5.3.0
 */
@Internal
public final class ExchangeAttempts {

    private ExchangeAttempts() {
    }

    /**
     * Whether the body of a request can be sent again: a body in memory can (an object, a
     * string, bytes, a buffered {@link ByteBody}), a streamed one cannot (a publisher, an
     * {@link InputStream}, a streamed {@link ByteBody}, a multipart body with an
     * {@link InputStream} part, a reference counted buffer).
     *
     * @param request        The request
     * @param replayableBody Whether the client can send a body object of another type again
     * @return Whether it can be sent again
     */
    public static boolean isReplayable(HttpRequest<?> request, Predicate<Object> replayableBody) {
        Object body = request.getBody().orElse(null);
        if (body == null) {
            return true;
        }
        if (body instanceof ByteBody byteBody) {
            return byteBody instanceof AvailableByteBody;
        }
        if (body instanceof MultipartBody multipart) {
            return multipart.isReplayable();
        }
        // a reference counted buffer is released once it is sent
        return !(body instanceof InputStream) && !(body instanceof ReferenceCounted)
            && !Publishers.isConvertibleToPublisher(body) && replayableBody.test(body);
    }

    /**
     * A copy of a request for an attempt, so that the filters of an attempt do not see the
     * changes the filters of an earlier attempt made. A body in memory is shared, a buffered
     * {@link ByteBody} is split.
     *
     * @param request The request of the exchange
     * @return The request of the attempt
     */
    public static MutableHttpRequest<?> copyForAttempt(HttpRequest<?> request) {
        MutableHttpRequest<Object> copy = HttpRequest.create(request.getMethod(), request.getUri().toString(), request.getMethodName());
        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                copy.getHeaders().add(name, value);
            }
        });
        copy.getAttributes().putAll(request.getAttributes());
        request.getBody().ifPresent(body -> copy.body(body instanceof AvailableByteBody available ? available.split() : body));
        return copy;
    }

    /**
     * Whether the status of the response of an attempt is retried: then the response is closed
     * and the attempt fails.
     *
     * @param attempt  The attempt, or {@code null} without retry
     * @param response The response, whose body is not read yet
     * @param <X>      The type of the flow
     * @return The failed flow of the attempt, or {@code null} if the response is passed on
     */
    public static <X> @Nullable ExecutionFlow<X> retriedStatus(RetryingExchange<?>.@Nullable Attempt attempt, ByteBodyHttpResponse<?> response) {
        if (attempt == null) {
            return null;
        }
        HttpClientException retried = attempt.retryStatus(response);
        if (retried == null) {
            return null;
        }
        response.close();
        return ExecutionFlow.error(retried);
    }
}
