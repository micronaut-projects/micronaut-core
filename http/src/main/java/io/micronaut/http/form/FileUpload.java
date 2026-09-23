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

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

/**
 * A file uploaded in a form: a file of a collected {@link FormData}, or the file view of a
 * {@link FormPart} that is still arriving. The same interface is used whether the content is in
 * memory, on disk or still being received.
 *
 * <h2>Single use</h2>
 * <p>The content can be consumed once: {@link #bytes(int)}, {@link #transferTo(Path)},
 * {@link #transferTo(OutputStream)}, {@link #readAllBytes()}, {@link #readString()} and
 * {@link #takeBody()} are mutually exclusive, whatever the storage. A second consumption, or a
 * consumption after {@link #close()}, fails synchronously with an {@link IllegalStateException},
 * and so does a consumption with invalid arguments. A consumption that fails once it has started
 * still consumes the upload, as the content cannot be replayed. The metadata stays readable after
 * the content was consumed or the upload closed.</p>
 *
 * <h2>Ownership and closing</h2>
 * <p>An upload is owned by the scope that handed it out: the request for a {@link FormData}, the
 * consumer callback for a {@link FormPart}. When the scope ends, what was not consumed is
 * released and an operation that is still running is aborted, so the stage of the work must be
 * part of the stage returned to the scope:</p>
 * <pre>{@code
 * return form.getFile("avatar").transferTo(destination)
 *     .thenApply(done -> HttpResponse.noContent());
 * }</pre>
 *
 * <p>Do not close an upload in a try-with-resources block around an asynchronous operation: the
 * block closes it as soon as the operation starts, which aborts it.</p>
 *
 * <p>{@link #close()} and {@link #closeAsync()} release the content early. Closing is idempotent:
 * closing again, concurrently or later, joins the same cleanup. Closing does not wait for disk
 * operations, and an operation it aborts completes with a {@link CancellationException}.
 * {@link #closeAsync()} completes when the resources owned by the upload are released: buffers,
 * temporary files and running disk operations. A file successfully written by
 * {@link #transferTo(Path)} belongs to the application and is never deleted by closing.</p>
 *
 * <p>The stages returned by this interface complete on a thread chosen by the server, e.g. an
 * I/O thread or a thread of the blocking executor that did the disk work; the dependent actions
 * of a stage are not guaranteed to run on a worker thread. Cancelling a stage derived from them
 * does not abort the operation: close the upload.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FileUpload extends AutoCloseable {

    /**
     * @return The name of the form field
     */
    String name();

    /**
     * @return The name of the file, as given by the client. Do not use it as a path without
     * sanitizing it.
     */
    String fileName();

    /**
     * @return The content type of the file, if given
     */
    Optional<MediaType> contentType();

    /**
     * The exact size of the content, only when the complete content is known: for a file of a
     * collected form, always; for a file that is still arriving, once it was received completely.
     *
     * @return The size in bytes, if known
     */
    OptionalLong size();

    /**
     * The size the client declared for the content, if any. It is only a hint: the limits are
     * enforced on the bytes actually received.
     *
     * @return The expected size in bytes, if known
     */
    OptionalLong expectedSize();

    /**
     * Read the whole content into memory.
     *
     * @param maximumBytes The maximum number of bytes to read, zero or more. The configured limits
     *                     of the server also apply
     * @return Completes with a copy of the content, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when the content is
     * larger than the limit
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the content was already consumed, or the upload closed
     */
    CompletionStage<byte[]> bytes(int maximumBytes);

    /**
     * Write the content to a new file. The file must not exist, and its directory must exist: the
     * content is written to a temporary file next to it that is then moved to the destination
     * without replacing an existing file. On failure, the temporary file is deleted, and an
     * existing destination is left as it was. Once the file was written, it belongs to the
     * application.
     *
     * @param destination The file to create. Do not derive it from {@link #fileName()} without
     *                    sanitizing it
     * @return Completes when the file was written, or exceptionally, e.g. with a
     * {@link java.nio.file.FileAlreadyExistsException} if the destination exists
     * @throws IllegalStateException if the content was already consumed, or the upload closed
     */
    CompletionStage<Void> transferTo(Path destination);

    /**
     * Write the content to a stream, which is flushed at the end and not closed.
     *
     * <p>The stream is written by the thread that delivers the content: an I/O thread of the
     * server for content that is still arriving, e.g. the file of a {@link FormPart}, and a
     * thread of the I/O executor for a stored upload of a {@link FormData}. A stream that blocks,
     * e.g. a network or file stream, must not be written on an I/O thread of the server: write it
     * on an executor, e.g. with {@link #takeBody()} and {@link CloseableByteBody#toInputStream()}
     * on the blocking executor. A stream in memory, such as a {@link java.io.ByteArrayOutputStream},
     * can be written anywhere.</p>
     *
     * @param out The stream
     * @return Completes when the content was written, or exceptionally when writing failed, or
     * with a {@link io.micronaut.http.exceptions.ContentLengthExceededException} when the content
     * is larger than the limit of the server for a file
     * @throws IllegalStateException if the content was already consumed, or the upload closed
     */
    CompletionStage<Void> transferTo(OutputStream out);

    /**
     * Read the whole content of an upload that was completely received, blocking: the uploads of
     * a {@link FormData}, which are stored in memory or on disk before the handler runs, for a
     * handler that runs on an executor. The content is consumed, like with {@link #bytes(int)}.
     * It is not available for an upload that is still arriving, e.g. the file of a
     * {@link FormPart}: read it with {@link #bytes(int)}.
     *
     * @return The content
     * @throws IllegalStateException if the upload is still arriving, the content was already
     * consumed, or the upload closed
     * @throws java.io.UncheckedIOException if reading the stored content fails
     */
    byte[] readAllBytes();

    /**
     * Read the whole content of an upload that was completely received as text, in the charset
     * of the request, blocking, like {@link #readAllBytes()}.
     *
     * @return The content
     * @throws IllegalStateException if the upload is still arriving, the content was already
     * consumed, or the upload closed
     * @throws java.io.UncheckedIOException if reading the stored content fails
     */
    String readString();

    /**
     * Take the content as a byte body: the caller becomes responsible for it. Nothing is read
     * before the body is consumed. The body follows the contract of {@link CloseableByteBody}: the
     * caller must consume it, or close it, and release what a consumption produced, e.g. close
     * an input stream or cancel a subscription. Closing the upload afterwards does not affect
     * the body.
     *
     * @return The content
     * @throws IllegalStateException if the content was already consumed, or the upload closed
     */
    CloseableByteBody takeBody();

    /**
     * Release the content: abort a running operation and release what was not consumed.
     *
     * @return Completes when the resources owned by the upload were released, or exceptionally
     * when releasing them failed. The same stage is returned on every call
     */
    CompletionStage<Void> closeAsync();

    /**
     * Start releasing the content like {@link #closeAsync()}, without waiting for it.
     */
    @Override
    void close();
}
