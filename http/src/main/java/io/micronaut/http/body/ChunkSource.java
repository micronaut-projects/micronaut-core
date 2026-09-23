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

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The elements of a streamed response body, pulled one at a time by the server as the connection
 * takes the bytes of the previous ones: the response side of {@link BodyElements}. Return it as
 * the body of a response, and the server writes the elements with the message body writer of the
 * content type of the response, exactly like the elements of a {@code Publisher} body, but
 * without Reactive Streams:
 *
 * <pre>{@code
 * routes.GET("/books", (request, variables) -> {
 *     Cursor<Book> cursor = books.open();
 *     return HttpResponse.ok((ChunkSource<Book>) () -> cursor.nextAsync())
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
 * and only while the bytes the connection has not taken yet stay below a high-water mark. A slow
 * client therefore pauses the source instead of buffering its elements.</p>
 *
 * <p>{@link #close()} is called once when the response ends: after the end, after a failure,
 * when the client disconnects, and when the body is not written at all (a {@code HEAD} request).
 * A source whose response is replaced before it is written (by a filter, for example) is not
 * closed, so a source should acquire its resources on the first {@link #next()}.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ChunkSource<T> extends AutoCloseable {

    /**
     * The next element. Called by the server, one call at a time.
     *
     * @return Completes with the next element, with an empty optional at the end of the body, or
     * exceptionally if producing the element failed
     */
    CompletionStage<Optional<T>> next();

    /**
     * Release the resources of the source: the response ended, failed, the client disconnected,
     * or the body is not written. Called once, and not while a {@link #next()} stage is pending
     * unless the client disconnected.
     */
    @Override
    default void close() {
    }
}
