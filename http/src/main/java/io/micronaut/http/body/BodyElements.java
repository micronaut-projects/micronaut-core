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
import java.util.function.Function;

/**
 * The elements of a body, decoded one at a time as they are asked for, like a cursor: a JSON
 * array or a JSON stream read with {@link io.micronaut.http.AsyncServerHttpRequest#elements}.
 * Nothing is read ahead of the caller: the next element is received and decoded when
 * {@link #next()} is called, or when the stage the {@link #forEach} consumer returned for the
 * previous element completes.
 *
 * <pre>{@code
 * request.elements(Person.class)
 *     .forEach(person -> people.saveAsync(person))
 *     .thenApply(done -> HttpResponse.accepted());
 * }</pre>
 *
 * <p>One operation at a time: an operation started while another one is in progress fails with
 * an {@link IllegalStateException}. The end of the body and closing are different: at the end of
 * the body, {@link #next()} completes with an empty optional and {@link #forEach} completes
 * normally, while closing during an operation completes that operation with a
 * {@link java.util.concurrent.CancellationException}, and an operation started after closing
 * fails with an {@link IllegalStateException}. Closing discards the rest of the body; the
 * elements are closed when the stage returned by the handler completes, and when the request
 * ends.</p>
 *
 * <p>The stages complete on a thread chosen by the server, usually an I/O thread: a consumer
 * must not block.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
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
     * for the previous one completes.
     *
     * @param consumer Consumes an element, completing when it is done with it
     * @return Completes when every remaining element was consumed, or exceptionally when reading
     * the body or a consumer fails
     * @throws IllegalStateException if another operation is in progress, or the elements were closed
     */
    CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer);

    /**
     * Discard the rest of the body. An operation that has not completed ends with a
     * {@link java.util.concurrent.CancellationException}. Closing again returns the same stage.
     *
     * @return Completes when the elements were closed
     */
    CompletionStage<Void> closeAsync();

    /**
     * Close like {@link #closeAsync()}, without waiting for it.
     */
    @Override
    void close();
}
