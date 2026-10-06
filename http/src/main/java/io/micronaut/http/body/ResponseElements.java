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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * The elements of a streamed response body, pulled one at a time by the server as the connection
 * takes the bytes of the previous ones: the response side of {@link BodyElements}. Return it as
 * the body of a response, and the server writes the elements with the message body writer of the
 * content type of the response, exactly like the elements of a {@code Publisher} body, but
 * without Reactive Streams:
 *
 * <pre>{@code
 * routes.GET("/books", (request, pathVariables) -> {
 *     Cursor<Book> cursor = books.open();
 *     return HttpResponse.ok(ResponseElements.of(cursor::nextAsync, cursor::close))
 *         .contentType(MediaType.APPLICATION_JSON_TYPE);
 * });
 * }</pre>
 *
 * <p>Like a {@code Publisher} body: with a JSON content type the elements are written as a JSON
 * array, with {@code text/event-stream} as server-sent events (an element may be an
 * {@link io.micronaut.http.sse.Event}), and otherwise one after the other. The response is sent
 * once the first element (or the end) is available: if the first {@link #next()} fails, the
 * failure is answered like a failure of the route. A later failure ends the response abruptly
 * (the connection is closed), since the status was already sent.</p>
 *
 * <p>Backpressure: {@link #next()} is called again only after the stage it returned completed,
 * and only while the bytes the connection has not taken yet stay below a high-water mark
 * ({@code micronaut.server.responses.stream.high-water-mark}). A slow client therefore pauses the
 * elements instead of buffering them. {@link #next()} is called on a thread of the server,
 * usually the event loop of the connection, or on the thread that completed the previous stage:
 * it must not block, and should return a stage that another thread completes if producing the
 * element takes time.</p>
 *
 * <p>{@link #close()} is called once when the response ends: after the end, after a failure, when
 * the client disconnects, when the body is not written at all (a {@code HEAD} request), and when a
 * filter replaces the response or its body. A filter that replaces them with other elements hands
 * them over: the new elements are closed instead, and close these if they wrap them.</p>
 *
 * <p>The elements are written like the elements of a {@code Publisher} body: with the writer of
 * their type, on a worker thread if the writer is blocking, and a
 * {@link io.micronaut.http.body.ByteBody} element as it is.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ResponseElements<T> extends AutoCloseable {

    /**
     * The next element. Called by the server, one call at a time.
     *
     * @return Completes with the next element, with an empty optional at the end of the body, or
     * exceptionally if producing the element failed
     */
    CompletionStage<Optional<T>> next();

    /**
     * Release the resources of the elements: the response ended, failed, the client disconnected,
     * or the body is not written. Called once, and not while a {@link #next()} stage is pending
     * unless the client disconnected.
     */
    @Override
    default void close() {
    }

    /**
     * The elements a function produces, e.g. the next row of a cursor.
     *
     * @param next Produces the next element, see {@link #next()}
     * @param <T>  The type of an element
     * @return The elements
     */
    static <T> ResponseElements<T> of(Supplier<? extends CompletionStage<Optional<T>>> next) {
        Objects.requireNonNull(next, "next");
        return next::get;
    }

    /**
     * The elements a function produces, with the resources a callback releases, e.g. the next row
     * of a cursor and closing the cursor.
     *
     * @param next  Produces the next element, see {@link #next()}
     * @param close Releases the resources, see {@link #close()}
     * @param <T>   The type of an element
     * @return The elements
     */
    static <T> ResponseElements<T> of(Supplier<? extends CompletionStage<Optional<T>>> next, Runnable close) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(close, "close");
        return new ResponseElements<>() {
            @Override
            public CompletionStage<Optional<T>> next() {
                return next.get();
            }

            @Override
            public void close() {
                close.run();
            }

            @Override
            public String toString() {
                return next.toString();
            }
        };
    }
}
