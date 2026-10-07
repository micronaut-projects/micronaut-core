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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The elements of a body, one at a time, like a cursor: pulled by whoever reads them.
 *
 * <h2>Reading a request body</h2>
 * <p>{@link AsyncRequestBody#elements} reads a JSON array or a JSON stream as elements, decoded
 * as they are asked for. Nothing is read ahead of the caller: the next element is received and
 * decoded when {@link #next()} is called, or when the stage the {@link #forEach} consumer
 * returned for the previous element completes.</p>
 *
 * <pre>{@code
 * request.elements(Person.class)
 *     .forEach(person -> people.saveAsync(person))
 *     .thenApply(done -> HttpResponse.accepted());
 * }</pre>
 *
 * <h2>Writing a response body</h2>
 * <p>Returned as the body of a response, the elements are pulled by the server as the connection
 * takes the bytes of the previous ones, and written with the message body writer of the content
 * type of the response, exactly like the elements of a {@code Publisher} body, but without
 * Reactive Streams. With a JSON content type they are written as a JSON array, with
 * {@code text/event-stream} as server-sent events (an element may be an
 * {@link io.micronaut.http.sse.Event}), and otherwise one after the other; a
 * {@link ByteBody} element is written as its bytes arrive. Elements read from a request body, or
 * by the HTTP client, can be returned as they are:</p>
 *
 * <pre>{@code
 * routes.GET("/books", (request, pathVariables) -> {
 *     Cursor<Book> cursor = books.open();
 *     return HttpResponse.ok(BodyElements.of(cursor::nextAsync, cursor::close))
 *         .contentType(MediaType.APPLICATION_JSON_TYPE);
 * });
 * routes.POST("/echo").body().handleAsync((request, pathVariables, body) ->
 *     CompletableFuture.completedStage(HttpResponse.ok(body.elements(Person.class))));
 * }</pre>
 *
 * <p>The response is sent once the first element (or the end) is available: if the first
 * {@link #next()} fails, the failure is answered like a failure of the route; a later failure
 * ends the response abruptly. {@link #next()} is called again only after the stage it returned
 * completed, and only while the bytes the connection has not taken yet stay below a high-water
 * mark ({@code micronaut.server.responses.stream.high-water-mark}), so a slow client pauses the
 * elements. It is called on a thread of the server, usually an event loop: it must not block, and
 * should return a stage that another thread completes if producing the element takes time.
 * {@link #close()} is called once when the response ends: after the end, after a failure, when
 * the client disconnects, when the body is not written (a {@code HEAD} request), and when a filter
 * replaces the response or its body. A filter that replaces them with other elements hands them
 * over: the new elements are closed instead, and close these if they wrap them.</p>
 *
 * <h2>The rules</h2>
 * <p>One operation at a time: an operation started while another one is in progress throws an
 * {@link IllegalStateException}, at once, instead of returning a stage. The end of the body and
 * closing are different: at the end of the body, {@link #next()} completes with an empty
 * optional and {@link #forEach} completes normally, while closing during an operation completes
 * that operation with a {@link java.util.concurrent.CancellationException}, and an operation
 * started after closing throws an {@link IllegalStateException}. An element that the elements of
 * {@link #of} produce after they were closed is not delivered: it is closed if it is
 * {@link AutoCloseable}, e.g. a {@link CloseableByteBody}. The elements of a request body
 * are closed when the method that read them completed, see {@link AsyncRequestBody}, unless they
 * are the body of its response; closing them discards the rest of the body.</p>
 *
 * <p>The instances of the framework, and those of {@link #of}, enforce the rules; a lambda does
 * not, and is only ever called by one caller at a time when the server writes it. The stages of
 * the instances of the framework complete on a thread chosen by the server, usually an I/O thread:
 * a consumer must not block.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface BodyElements<T> extends AutoCloseable {

    /**
     * Read the next element.
     *
     * @return Completes with the element, with an empty optional at the end of the body, or
     * exceptionally when reading or decoding the body fails
     * @throws IllegalStateException if another operation is in progress, or the elements were closed
     */
    CompletionStage<Optional<T>> next();

    /**
     * Consume the remaining elements in order. The next element is read when the stage returned
     * for the previous one completes. Elements that are available at once are consumed in a loop,
     * so many of them do not deepen the stack. A failure of a consumer does not close the
     * elements.
     *
     * @param consumer Consumes an element, completing when it is done with it
     * @return Completes when every remaining element was consumed, or exceptionally when reading
     * the body or a consumer fails
     * @throws IllegalStateException if another operation is in progress, or the elements were closed
     */
    default CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<Void> result = new CompletableFuture<>();
        BodyElementsLoop.run(this::next, consumer, result);
        return result;
    }

    /**
     * Close the elements, e.g. discard the rest of the body. An operation that has not completed
     * ends with a {@link java.util.concurrent.CancellationException}. For the instances of the
     * framework, closing again returns the same stage.
     *
     * @return Completes when the elements were closed, or exceptionally if closing failed
     */
    default CompletionStage<Void> closeAsync() {
        try {
            close();
            return CompletableFuture.completedStage(null);
        } catch (Throwable e) {
            return CompletableFuture.failedStage(e);
        }
    }

    /**
     * Close like {@link #closeAsync()}, without waiting for it. Does nothing by default: elements
     * that hold resources, e.g. a database cursor, release them here.
     */
    @Override
    default void close() {
    }

    /**
     * The elements a function produces, e.g. the next row of a cursor. The elements enforce the
     * rules, see the class documentation.
     *
     * @param next Produces the next element, see {@link #next()}
     * @param <T>  The type of an element
     * @return The elements
     */
    static <T> BodyElements<T> of(Supplier<? extends CompletionStage<Optional<T>>> next) {
        return new SuppliedBodyElements<>(Objects.requireNonNull(next, "next"), null);
    }

    /**
     * The elements a function produces, with the resources a callback releases once, e.g. the
     * next row of a cursor and closing the cursor. The elements enforce the rules, see the class
     * documentation.
     *
     * @param next  Produces the next element, see {@link #next()}
     * @param close Releases the resources, see {@link #close()}
     * @param <T>   The type of an element
     * @return The elements
     */
    static <T> BodyElements<T> of(Supplier<? extends CompletionStage<Optional<T>>> next, Runnable close) {
        return new SuppliedBodyElements<>(Objects.requireNonNull(next, "next"), Objects.requireNonNull(close, "close"));
    }
}
