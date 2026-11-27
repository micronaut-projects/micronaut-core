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
package io.micronaut.http.server.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.InputStreamByteBody;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * A {@link CloseableByteBody} that holds a range of a file. A server transport that can send the
 * bytes of a file directly to the connection, without copying them to user space, takes the range
 * with {@link #claimRegion()}. Any other operation reads the file as a stream on the IO executor.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class FileByteBody extends InternalByteBody implements CloseableByteBody {
    private final long position;
    private final long length;
    private final Executor ioExecutor;
    private final ByteBodyFactory bodyFactory;
    /**
     * The open file, while the range was neither claimed nor turned into a stream.
     */
    private @Nullable FileInputStream file;
    /**
     * The stream body that the file was turned into for an operation other than
     * {@link #claimRegion()}.
     */
    private @Nullable CloseableByteBody stream;

    /**
     * Create a body for a range of a file. Ownership of the file is transferred to the body.
     *
     * @param file        The open file
     * @param position    The position of the first byte of the range
     * @param length      The length of the range
     * @param ioExecutor  The executor for blocking reads
     * @param bodyFactory The body factory for buffer-based operations
     */
    FileByteBody(FileInputStream file, long position, long length, Executor ioExecutor, ByteBodyFactory bodyFactory) {
        this.file = file;
        this.position = position;
        this.length = length;
        this.ioExecutor = ioExecutor;
        this.bodyFactory = bodyFactory;
    }

    /**
     * Whether the range can still be claimed with {@link #claimRegion()}: neither the range nor
     * the bytes of this body were claimed yet, and it was not closed.
     *
     * @return {@code true} iff the range can be claimed
     */
    public boolean isRegionAvailable() {
        return file != null;
    }

    /**
     * Claim the range of the file. The caller owns the returned channel and closes it once the
     * range is sent, which also closes the file.
     *
     * @return The range
     * @throws IllegalStateException if the body was claimed already, see {@link #isRegionAvailable()}
     */
    public Region claimRegion() {
        FileInputStream f = file;
        if (f == null) {
            failClaim();
            // failClaim always throws, this tells the compiler and static analysis
            throw new IllegalStateException("Body already claimed");
        }
        recordPrimaryOp();
        file = null;
        return new Region(f.getChannel(), position, length);
    }

    /**
     * The stream body that the file is turned into, created on first use. The result is typed as
     * a {@link ByteBody} because this body keeps owning it.
     *
     * @return The stream body
     */
    private ByteBody stream() {
        CloseableByteBody s = stream;
        if (s == null) {
            FileInputStream f = file;
            if (f == null) {
                failClaim();
                // failClaim always throws, this tells the compiler and static analysis
                throw new IllegalStateException("Body already claimed");
            }
            file = null;
            s = InputStreamByteBody.create(new RangeInputStream(f, position, length), OptionalLong.of(length), ioExecutor, bodyFactory);
            stream = s;
        }
        return s;
    }

    @Override
    public CloseableByteBody allowDiscard() {
        if (file == null) {
            stream().allowDiscard();
        }
        // an unread file has no upstream to drain: it is always discardable
        return this;
    }

    @Override
    public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        return stream().split(backpressureMode);
    }

    @Override
    public OptionalLong expectedLength() {
        return OptionalLong.of(length);
    }

    @Override
    public InputStream toInputStream() {
        return stream().toInputStream();
    }

    @Override
    public Publisher<byte[]> toByteArrayPublisher() {
        return stream().toByteArrayPublisher();
    }

    @Override
    public Publisher<ReadBuffer> toReadBufferPublisher() {
        return stream().toReadBufferPublisher();
    }

    @Override
    public Publisher<ReadBuffer> toUnbufferedReadBufferPublisher() {
        return InternalByteBody.toUnbufferedReadBufferPublisher(stream());
    }

    @Override
    public ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        return InternalByteBody.bufferFlow(stream());
    }

    @Override
    public CloseableByteBody move() {
        FileInputStream f = file;
        if (f != null) {
            // the moved body can still be sent as a range of the file
            recordPrimaryOp();
            file = null;
            return new FileByteBody(f, position, length, ioExecutor, bodyFactory);
        }
        return stream().move();
    }

    @Override
    public void close() {
        FileInputStream f = file;
        CloseableByteBody s = stream;
        file = null;
        stream = null;
        if (f != null || s != null) {
            recordClosed();
        }
        if (f != null) {
            try {
                f.close();
            } catch (IOException ignored) {
                // nothing was read from the file
            }
        }
        if (s != null) {
            s.close();
        }
    }

    /**
     * A range of an open file.
     *
     * @param channel  The channel of the file, owned by the receiver of the range
     * @param position The position of the first byte of the range
     * @param length   The length of the range
     */
    public record Region(FileChannel channel, long position, long length) {
    }

    /**
     * A stream of a range of another stream.
     */
    private static final class RangeInputStream extends InputStream {
        private final InputStream delegate;
        private final long toSkip;
        private long remainingLength;
        private boolean skipped = false;
        private boolean skipSuccess = false;

        private RangeInputStream(InputStream delegate, long toSkip, long length) {
            this.delegate = delegate;
            this.toSkip = toSkip;
            this.remainingLength = length;

            if (toSkip == 0) {
                skipped = true;
                skipSuccess = true;
            }
        }

        private boolean doSkip() throws IOException {
            if (!skipped) {
                skipped = true;
                try {
                    delegate.skipNBytes(toSkip);
                    skipSuccess = true;
                } catch (EOFException ignored) {
                    // the file is shorter than the range start, skipSuccess stays false
                }
            }
            return skipSuccess;
        }

        @Override
        public int read() throws IOException {
            if (!doSkip()) {
                return -1;
            }
            if (remainingLength <= 0) {
                return -1;
            }
            int read = delegate.read();
            if (read != -1) {
                remainingLength--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!doSkip()) {
                return -1;
            }
            if (remainingLength <= 0) {
                return -1;
            }
            if (len > remainingLength) {
                len = (int) remainingLength;
            }
            int n = delegate.read(b, off, len);
            if (n != -1) {
                remainingLength -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
