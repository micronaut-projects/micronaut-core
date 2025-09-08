/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.ByteBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Extended InputStream API for better backpressure/cancellation handling.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Internal
abstract class ExtendedInputStream extends InputStream {
    // originally from micronaut-servlet

    private static final int CHUNK_SIZE = 8192;
    private static final Logger LOG = LoggerFactory.getLogger(ExtendedInputStream.class);

    static ExtendedInputStream wrap(InputStream inputStream) {
        return new Wrapper(inputStream);
    }

    @Override
    public int read() throws IOException {
        byte[] arr1 = new byte[1];
        int n = read(arr1);
        if (n == -1) {
            return -1;
        } else if (n == 0) {
            throw new IllegalStateException("Read 0 bytes");
        } else {
            return arr1[0] & 0xff;
        }
    }

    @Override
    public abstract int read(byte[] b, int off, int len) throws IOException;

    /**
     * Read some data into a new byte array. The array may be of any size. This is usually the same
     * as allocating a new array, calling {@link #read(byte[])}, and then truncating the array, but
     * may be optimized in some implementations.
     */
    public byte @Nullable [] readSome() throws IOException {
        byte[] arr = new byte[CHUNK_SIZE];
        int n = read(arr);
        if (n == -1) {
            return null;
        } else if (n == arr.length) {
            return arr;
        } else {
            return Arrays.copyOf(arr, n);
        }
    }

    @Override
    public void close() {
        allowDiscard();
        cancelInput();
    }

    /**
     * Allow discarding the input of this stream. See {@link ByteBody#allowDiscard()}.
     */
    public abstract void allowDiscard();

    /**
     * Cancel any further upstream input. This also removes any backpressure that this stream
     * may apply on its upstream.
     */
    public abstract void cancelInput();

    private static final class Wrapper extends ExtendedInputStream {
        private final Lock lock = new ReentrantLock();
        private final InputStream delegate;
        private boolean discarded;

        Wrapper(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                if (discarded) {
                    throw ByteBody.BodyDiscardedException.create();
                }
                return delegate.read(b, off, len);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (IOException e) {
                LOG.debug("Failed to close request stream", e);
            }
        }

        @Override
        public void allowDiscard() {
            lock.lock();
            try {
                discarded = true;
                close();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void cancelInput() {
        }
    }
}
