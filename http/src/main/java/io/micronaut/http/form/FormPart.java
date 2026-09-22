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
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A part of a form read with {@link FormParts}: a text field, or a file of a
 * {@code multipart/form-data} form.
 *
 * <p>The content can be consumed once, with {@link #text()}, {@link #bytes(int)},
 * {@link #transferTo(Path)} or {@link #takeBody()}, or through the {@link #file()} view, which
 * shares the content and the lifecycle of the part: consuming or closing one consumes or closes
 * the other. See {@link FileUpload} for the rules of consumption and closing.</p>
 *
 * <p>The part is owned by the consumer callback it was given to: when the stage the callback
 * returned completes, what was not consumed is discarded and an operation that is still running
 * is aborted, before the next part is read. The callback must therefore return the stage of its
 * work:</p>
 * <pre>{@code
 * parts.part("file", part -> part.file().transferTo(destination))
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FormPart extends AutoCloseable {

    /**
     * @return The name of the field
     */
    String name();

    /**
     * @return The name of the uploaded file, or {@code null} for a text field
     */
    @Nullable String fileName();

    /**
     * @return Whether the part is an uploaded file
     */
    default boolean isFile() {
        return fileName() != null;
    }

    /**
     * @return The content type of the part, if given
     */
    Optional<MediaType> contentType();

    /**
     * The part as a file upload. This does not read or claim the content: the returned upload is a
     * view of this part, and the same view is returned on every call.
     *
     * @return The file
     * @throws FormFieldException if the part is not a file, answered with 400
     */
    FileUpload file();

    /**
     * Read the whole content as text, in the charset of the request, with the limit of the server
     * for buffered request content ({@code micronaut.server.max-request-buffer-size}). Malformed
     * input is replaced, like in the text fields of a collected form.
     *
     * @return Completes with the content
     * @throws IllegalStateException if the content was already consumed, or the part closed
     */
    CompletionStage<String> text();

    /**
     * Read the whole content as text, in the charset of the request.
     *
     * @param maximumBytes The maximum number of bytes to read, not characters, zero or more
     * @return Completes with the content, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when the content is
     * larger than the limit
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the content was already consumed, or the part closed
     */
    CompletionStage<String> text(int maximumBytes);

    /**
     * Read the whole content into memory.
     *
     * @param maximumBytes The maximum number of bytes to read, zero or more
     * @return Completes with a copy of the content
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the content was already consumed, or the part closed
     * @see FileUpload#bytes(int)
     */
    CompletionStage<byte[]> bytes(int maximumBytes);

    /**
     * Write the content to a new file, a text field as well as a file.
     *
     * @param destination The file to create; it must not exist
     * @return Completes when the file was written
     * @throws IllegalStateException if the content was already consumed, or the part closed
     * @see FileUpload#transferTo(Path)
     */
    CompletionStage<Void> transferTo(Path destination);

    /**
     * Take the content as a byte body: the caller becomes responsible for it. In a
     * {@link FormParts} callback, the body must be consumed or closed before the stage returned
     * by the callback completes: the next part cannot be read before the content of this one.
     *
     * @return The content
     * @throws IllegalStateException if the content was already consumed, or the part closed
     * @see FileUpload#takeBody()
     */
    CloseableByteBody takeBody();

    /**
     * Discard the content: abort a running operation and release what was not consumed.
     *
     * @return Completes when the resources owned by the part were released. The same stage is
     * returned on every call
     */
    CompletionStage<Void> closeAsync();

    /**
     * Start discarding the content like {@link #closeAsync()}, without waiting for it.
     */
    @Override
    void close();
}
