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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Experimental;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The parts of a submitted form, {@code application/x-www-form-urlencoded} or
 * {@code multipart/form-data}, read forward as they arrive, like a cursor. Nothing is buffered:
 * each part is handed to a consumer, and a part that is skipped or not read is discarded.
 *
 * <p>Read every part:</p>
 * <pre>{@code
 * routes.handleFormStream(HttpMethod.POST, "/upload", (request, pathVariables, parts) ->
 *     parts.forEach(part -> part.isFile()
 *             ? part.file().transferTo(uploads.resolve(UUID.randomUUID().toString()))
 *             : part.text(8_192).thenAccept(value -> fields.put(part.name(), value)))
 *         .thenApply(done -> HttpResponse.ok()));
 * }</pre>
 *
 * <p>Or read only the parts that are needed, in the order the client sends them, and throw out
 * the rest:</p>
 * <pre>{@code
 * parts.part("title", part -> part.text(8_192).thenAccept(title::set))
 *     .thenCompose(found -> parts.part("avatar", part -> part.file().transferTo(path)))
 *     .thenCompose(found -> parts.closeAsync()
 *         .thenApply(closed -> found ? HttpResponse.noContent() : HttpResponse.badRequest()));
 * }</pre>
 *
 * <p>One operation at a time: start the next one when the stage of the previous one completed;
 * an operation started while another one is in progress fails with an
 * {@link IllegalStateException}. A consumer callback owns its part until the stage it returned
 * completes; then what it did not consume is discarded, and the next part is only read once that
 * is done. A callback that returns {@code null} fails the operation.</p>
 *
 * <p>The end of the form and closing are different: at the end of the form, {@link #part} completes
 * with {@code false} and {@link #forEach} completes normally, while closing the parts during an
 * operation completes that operation with a {@link java.util.concurrent.CancellationException},
 * and an operation started after closing fails with an {@link IllegalStateException}. The parts
 * are closed when the stage returned by the handler completes, and when the request ends.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FormParts extends AutoCloseable {

    /**
     * Consume the remaining parts of the form in the order they arrive. The next part is read
     * when the stage returned for the previous one completes, and the content a consumer did not
     * read is discarded.
     *
     * @param consumer Consumes a part, completing when it is done with it
     * @return Completes when every remaining part is consumed, or exceptionally when reading the
     * form or a consumer fails
     */
    CompletionStage<Void> forEach(Function<? super FormPart, ? extends CompletionStage<?>> consumer);

    /**
     * Read forward to the next part with the given name and consume it. The parts before it are
     * discarded, and the parts after it are left for the next operation: a part the client sent
     * earlier than the current position is not found.
     *
     * @param name     The name of the part
     * @param consumer Consumes the part, completing when it is done with it
     * @return Completes with {@code true} when the part was consumed, {@code false} when the form
     * ended without it, or exceptionally when reading the form or the consumer fails
     */
    CompletionStage<Boolean> part(String name, Function<? super FormPart, ? extends CompletionStage<?>> consumer);

    /**
     * Throw out the rest of the form without reading it, and discard the part a consumer holds.
     * An operation that has not completed ends with a
     * {@link java.util.concurrent.CancellationException}. Closing again returns the same stage.
     *
     * @return Completes when the part a consumer held was released
     */
    CompletionStage<Void> closeAsync();

    /**
     * Start closing like {@link #closeAsync()}, without waiting for it.
     */
    @Override
    void close();
}
