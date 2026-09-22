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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.multipart.FormFieldMetadata;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The content of a form field and its lifecycle: the single owner shared by a {@link DefaultFormPart},
 * its file view and the {@link DefaultFileUpload}s of a collected form.
 *
 * <p>States: available, active (one operation runs), consumed (an operation ended), moved (the
 * body was taken) and closed. One operation at a time, and only once; closing is idempotent and
 * joins one cleanup.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract sealed class UploadContent permits StreamingUploadContent, StoredUploadContent {

    private static final int AVAILABLE = 0;
    private static final int ACTIVE = 1;
    private static final int CONSUMED = 2;
    private static final int MOVED = 3;
    private static final int CLOSED = 4;

    final FormFieldMetadata metadata;
    final UploadContext context;

    // guarded by this
    private int state = AVAILABLE;
    private @Nullable Operation<?> active;
    private @Nullable CompletionStage<Void> closedView;

    UploadContent(FormFieldMetadata metadata, UploadContext context) {
        this.metadata = metadata;
        this.context = context;
    }

    final String name() {
        return Objects.requireNonNullElse(metadata.name(), "");
    }

    final @Nullable String fileName() {
        return metadata.fileName();
    }

    final Optional<MediaType> contentType() {
        return Optional.ofNullable(metadata.mediaType());
    }

    abstract OptionalLong size();

    abstract OptionalLong expectedSize();

    /**
     * @param limit The limit
     * @return The operation that reads the content into memory
     */
    abstract Operation<byte[]> newBytes(long limit);

    /**
     * @param destination The destination
     * @return The operation that writes the content to a {@link #createStaging staging file} and
     * {@link #publish publishes} it
     */
    abstract Operation<Void> newTransfer(Path destination);

    /**
     * @return The content, moved to the caller
     */
    abstract CloseableByteBody moveBody();

    /**
     * Release the content that was not consumed.
     *
     * @return Completes when released
     */
    abstract CompletableFuture<Void> release();

    /**
     * @return The limit for content of this field, whatever the caller asks for
     */
    final long fieldLimit() {
        return fileName() != null ? context.maxFileSize() : Long.MAX_VALUE;
    }

    final CompletionStage<byte[]> bytes(int maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("The maximum number of bytes must not be negative: " + maximumBytes);
        }
        return run(newBytes(Math.min(maximumBytes, fieldLimit())));
    }

    final CompletionStage<String> text(int maximumBytes) {
        return bytes(maximumBytes).thenApply(bytes -> new String(bytes, context.charset()));
    }

    final CompletionStage<Void> transferTo(Path destination) {
        Objects.requireNonNull(destination, "destination");
        return run(newTransfer(destination));
    }

    final CloseableByteBody takeBody() {
        synchronized (this) {
            checkAvailable();
            state = MOVED;
        }
        return moveBody();
    }

    final CompletionStage<Void> closeAsync() {
        boolean releaseContent = false;
        Operation<?> running = null;
        CompletableFuture<Void> future;
        synchronized (this) {
            if (closedView != null) {
                return closedView;
            }
            future = new CompletableFuture<>();
            closedView = future.minimalCompletionStage();
            if (state == AVAILABLE) {
                releaseContent = true;
            } else if (state == ACTIVE) {
                running = active;
            }
            state = CLOSED;
        }
        CompletableFuture<Void> cleanup;
        if (releaseContent) {
            try {
                cleanup = release();
            } catch (Throwable e) {
                cleanup = CompletableFuture.failedFuture(e);
            }
        } else if (running != null) {
            running.abort();
            cleanup = running.cleanup;
        } else {
            cleanup = CompletableFuture.completedFuture(null);
        }
        cleanup.whenComplete((ignored, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(null);
            }
        });
        synchronized (this) {
            return Objects.requireNonNull(closedView);
        }
    }


    // called holding the lock
    private void checkAvailable() {
        if (state == CLOSED) {
            throw new IllegalStateException("The form field " + name() + " was closed");
        }
        if (state != AVAILABLE) {
            throw new IllegalStateException("The content of form field " + name() + " was already consumed");
        }
    }

    /**
     * Claim the content for an operation, and start it. The operation may be aborted by a close
     * before it starts.
     */
    private <T> CompletionStage<T> run(Operation<T> operation) {
        synchronized (this) {
            checkAvailable();
            state = ACTIVE;
            active = operation;
        }
        operation.cleanup.whenComplete((ignored, error) -> {
            synchronized (this) {
                if (active == operation) {
                    active = null;
                }
                if (state == ACTIVE) {
                    state = CONSUMED;
                }
            }
        });
        try {
            operation.start();
        } catch (Throwable e) {
            operation.settle(null, e, null);
        }
        return operation.result.minimalCompletionStage();
    }

    static ContentLengthExceededException tooLarge(String name, long limit) {
        return new ContentLengthExceededException("The form field [" + name + "] exceeds the maximum allowed content length [" + limit + "]");
    }

    /**
     * Create the file the content is written to before it is published: next to the destination,
     * so publishing is a rename in the same directory. Blocking.
     *
     * @param destination The destination
     * @return The staging file
     * @throws IOException if the file cannot be created, e.g. the directory does not exist
     */
    static Path createStaging(Path destination) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory == null) {
            throw new IOException("No directory for the destination " + destination);
        }
        if (Files.exists(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            // fail early; the move without replacing checks again
            throw new java.nio.file.FileAlreadyExistsException(destination.toString());
        }
        return Files.createTempFile(directory, ".upload-", ".tmp");
    }

    /**
     * Publish the staging file to the destination, without replacing an existing file. Blocking.
     *
     * @param staging     The staging file
     * @param destination The destination
     * @throws IOException if the destination exists, or the move failed
     */
    static void publish(Path staging, Path destination) throws IOException {
        Files.move(staging, destination);
    }

    /**
     * Delete a file if it exists, collecting the failure.
     *
     * @param file    The file
     * @param failure The failure so far
     * @return The failure
     */
    static @Nullable Throwable deleteQuietly(@Nullable Path file, @Nullable Throwable failure) {
        if (file == null) {
            return failure;
        }
        try {
            Files.deleteIfExists(file);
            return failure;
        } catch (IOException e) {
            if (failure != null) {
                failure.addSuppressed(e);
                return failure;
            }
            return e;
        }
    }

    /**
     * One operation on the content. It releases its resources before its result settles.
     *
     * @param <T> The result
     */
    abstract static class Operation<T> {
        /**
         * The result.
         */
        final CompletableFuture<T> result = new CompletableFuture<>();
        /**
         * Completes when the resources of the operation are released, exceptionally when that
         * failed.
         */
        final CompletableFuture<Void> cleanup = new CompletableFuture<>();

        /**
         * Start the operation. Does nothing if it was aborted before.
         */
        abstract void start();

        /**
         * Abort the operation, if it has not ended: it releases its resources and settles with a
         * {@link java.util.concurrent.CancellationException}.
         */
        abstract void abort();

        /**
         * Settle the operation once its resources are released.
         *
         * @param value        The value
         * @param error        The failure of the operation
         * @param cleanupError The failure to release the resources
         */
        final void settle(@Nullable T value, @Nullable Throwable error, @Nullable Throwable cleanupError) {
            if (cleanupError != null) {
                cleanup.completeExceptionally(cleanupError);
            } else {
                cleanup.complete(null);
            }
            if (error != null) {
                if (cleanupError != null && cleanupError != error) {
                    error.addSuppressed(cleanupError);
                }
                result.completeExceptionally(error);
            } else if (cleanupError != null) {
                result.completeExceptionally(cleanupError);
            } else {
                result.complete(value);
            }
        }
    }
}
