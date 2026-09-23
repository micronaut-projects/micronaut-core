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
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.InputStreamByteBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * The content of an uploaded file of a collected form, in memory or on disk. This owns the
 * upload: it was {@link CompletedFileUpload#moveResource() moved} from the one the request
 * disposes of, so the request cannot delete or release content this still owns or handed on.
 * Disk work runs on the I/O executor.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StoredUploadContent extends UploadContent {

    private final CompletedFileUpload upload;
    private final long size;

    /**
     * @param upload  The upload, owned by this
     * @param context The context
     */
    StoredUploadContent(CompletedFileUpload upload, UploadContext context) {
        super(upload.getMetadata(), context);
        this.upload = upload;
        this.size = upload.getSize();
    }

    @Override
    OptionalLong size() {
        return OptionalLong.of(size);
    }

    @Override
    OptionalLong expectedSize() {
        return OptionalLong.of(size);
    }

    @Override
    UploadContent.Operation<byte[]> newBytes(long limit) {
        return new Blocking<>() {
            @Override
            byte[] work() throws IOException {
                if (size > limit) {
                    throw tooLarge(name(), limit);
                }
                return upload.getBytes();
            }
        };
    }

    @Override
    UploadContent.Operation<Void> newTransfer(Path destination) {
        return new Blocking<>() {
            private @Nullable Path staging;

            @Override
            @Nullable Void work() throws IOException {
                staging = createStaging(destination);
                // replaces the staging file this created: a move of the temporary file, or a write
                upload.transferTo(staging);
                checkAborted();
                publish(staging, destination);
                staging = null;
                return null;
            }

            @Override
            @Nullable Throwable cleanupAfterFailure() {
                Throwable error = deleteQuietly(staging, null);
                staging = null;
                return error;
            }
        };
    }

    @Override
    UploadContent.Operation<Void> newStreamTransfer(OutputStream out) {
        return new Blocking<>() {
            @Override
            @Nullable Void work() throws IOException {
                try (InputStream in = upload.getInputStream()) {
                    in.transferTo(out);
                }
                out.flush();
                return null;
            }
        };
    }

    @Override
    boolean isComplete() {
        return true;
    }

    @Override
    byte[] readComplete() throws IOException {
        try {
            return upload.getBytes();
        } finally {
            upload.close();
        }
    }

    @Override
    CloseableByteBody moveBody() {
        if (upload.isInMemory()) {
            try {
                CloseableByteBody body = context.byteBodyFactory().adapt(upload.toReadBuffer());
                closeUpload();
                return body;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read the form field " + name(), e);
            }
        }
        // opened lazily, by the executor that reads the body
        return InputStreamByteBody.create(new LazyFileStream(), OptionalLong.of(size), context.ioExecutor(), context.byteBodyFactory());
    }

    @Override
    CompletableFuture<Void> release() {
        if (upload.isInMemory()) {
            closeUpload();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> released = new CompletableFuture<>();
        try {
            context.ioExecutor().execute(() -> {
                try {
                    upload.close();
                    released.complete(null);
                } catch (Throwable e) {
                    released.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            released.completeExceptionally(e);
        }
        return released;
    }

    private void closeUpload() {
        try {
            upload.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * An operation that runs on the I/O executor, in one task. Aborting it before it runs skips
     * it; aborting it while it runs is checked between its steps.
     *
     * @param <T> The result
     */
    private abstract class Blocking<T> extends UploadContent.Operation<T> {
        private volatile boolean aborted;

        abstract @Nullable T work() throws IOException;

        @Nullable Throwable cleanupAfterFailure() {
            return null;
        }

        final void checkAborted() {
            if (aborted) {
                throw new CancellationException("The form field " + name() + " was closed");
            }
        }

        @Override
        final void start() {
            try {
                context.ioExecutor().execute(this::runTask);
            } catch (RejectedExecutionException e) {
                finish(null, e);
            }
        }

        private void runTask() {
            T value = null;
            Throwable error = null;
            try {
                checkAborted();
                value = work();
            } catch (Throwable e) {
                error = e;
            }
            finish(value, error);
        }

        private void finish(@Nullable T value, @Nullable Throwable error) {
            Throwable cleanupError = error != null ? cleanupAfterFailure() : null;
            try {
                // the content was consumed or has to be released: the upload is done
                upload.close();
            } catch (Throwable e) {
                if (cleanupError == null) {
                    cleanupError = e;
                } else {
                    cleanupError.addSuppressed(e);
                }
            }
            settle(value, error, cleanupError);
        }

        @Override
        final void abort() {
            aborted = true;
        }
    }

    /**
     * The file of the upload, opened on the first read, which happens on the I/O executor. The
     * file is deleted when the stream is closed.
     */
    private final class LazyFileStream extends InputStream {
        private @Nullable InputStream delegate;
        private boolean closed;

        private InputStream delegate() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            InputStream d = delegate;
            if (d == null) {
                d = upload.getInputStream();
                delegate = d;
            }
            return d;
        }

        @Override
        public synchronized int read() throws IOException {
            return delegate().read();
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            return delegate().read(b, off, len);
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            InputStream d = delegate;
            delegate = null;
            try {
                if (d != null) {
                    d.close();
                }
            } finally {
                upload.closeAsync(context.ioExecutor());
            }
        }
    }
}
