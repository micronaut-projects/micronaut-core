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
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
     * {@link InputStream}, a streamed {@link ByteBody}).
     *
     * @param request The request
     * @return Whether it can be sent again
     */
    public static boolean isReplayable(HttpRequest<?> request) {
        if (request instanceof RawHttpRequestWrapper<?> raw && !raw.isBodyReplaced()) {
            return raw.byteBody() instanceof AvailableByteBody;
        }
        Object body = request.getBody().orElse(null);
        if (body == null) {
            return true;
        }
        if (body instanceof ByteBody byteBody) {
            return byteBody instanceof AvailableByteBody;
        }
        return !(body instanceof InputStream) && !Publishers.isConvertibleToPublisher(body);
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

    /**
     * @param flow   A flow
     * @param action Runs once the flow completed, or was cancelled
     * @param <X>    The type of the flow
     * @return The flow
     */
    public static <X> ExecutionFlow<X> whenDone(ExecutionFlow<X> flow, Runnable action) {
        DelayedExecutionFlow<X> done = DelayedExecutionFlow.create();
        flow.onComplete((value, failure) -> {
            action.run();
            if (failure != null) {
                done.completeExceptionally(failure);
            } else {
                done.complete(value);
            }
        });
        done.onCancel(() -> {
            flow.cancel();
            action.run();
        });
        return done;
    }

    /**
     * Release a response that nobody reads.
     *
     * @param response The response
     */
    public static void closeResponse(HttpResponse<?> response) {
        if (response instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                // the response is discarded
            }
        }
    }

    /**
     * The bodies of the attempts of a raw exchange: the buffered body of the request is split for
     * every attempt up front, by the caller, rather than on the thread that completes an attempt.
     * The splits no attempt took are closed with {@link #close()}.
     */
    public static final class RawBodies {
        private final List<@Nullable CloseableByteBody> splits;

        private RawBodies(List<@Nullable CloseableByteBody> splits) {
            this.splits = splits;
        }

        /**
         * @param request     The request of the exchange
         * @param maxAttempts The maximum number of attempts, {@code 0} without retry
         * @return The bodies of the attempts, empty if the request is not a raw request with a
         * buffered body, or is sent once
         */
        public static RawBodies of(HttpRequest<?> request, int maxAttempts) {
            List<@Nullable CloseableByteBody> splits = new ArrayList<>();
            if (maxAttempts > 1 && request instanceof RawHttpRequestWrapper<?> raw && !raw.isBodyReplaced()
                && raw.byteBody() instanceof AvailableByteBody body) {
                for (int i = 0; i < maxAttempts; i++) {
                    splits.add(body.split());
                }
            }
            return new RawBodies(splits);
        }

        /**
         * @return Whether there is no split
         */
        public boolean isEmpty() {
            return splits.isEmpty();
        }

        /**
         * @param number The number of the attempt, from {@code 1}
         * @return The body of the attempt, which it takes over
         */
        public synchronized CloseableByteBody forAttempt(int number) {
            return Objects.requireNonNull(splits.set(number - 1, null), "body of the attempt");
        }

        /**
         * Close the splits that no attempt took.
         */
        public synchronized void close() {
            for (int i = 0; i < splits.size(); i++) {
                CloseableByteBody unused = splits.set(i, null);
                if (unused != null) {
                    unused.close();
                }
            }
        }
    }
}
