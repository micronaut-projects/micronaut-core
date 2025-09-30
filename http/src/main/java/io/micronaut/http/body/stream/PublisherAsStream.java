/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.io.buffer.ReadBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * Transform a {@link PublisherAsBlocking} of buffers into a {@link InputStream}.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
public final class PublisherAsStream extends ExtendedInputStream {
    private final PublisherAsBlocking publisherAsBlocking;
    private ReadBuffer buffer;

    public PublisherAsStream(PublisherAsBlocking publisherAsBlocking) {
        this.publisherAsBlocking = publisherAsBlocking;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (!populateBuffer()) {
            return -1;
        }

        int readable = buffer.readable();
        if (len >= readable) {
            buffer.toArray(b, off);
            buffer = null;
            return readable;
        } else {
            ReadBuffer piece = buffer.split(len);
            piece.toArray(b, off);
            return len;
        }
    }

    @Override
    public byte @Nullable [] readSome() throws IOException {
        if (!populateBuffer()) {
            return null;
        }
        byte[] array = buffer.toArray();
        buffer = null;
        return array;
    }

    private boolean populateBuffer() throws IOException {
        while (buffer == null) {
            try {
                ReadBuffer o = publisherAsBlocking.take();
                if (o == null) {
                    Throwable failure = publisherAsBlocking.getFailure();
                    if (failure == null) {
                        return false;
                    } else {
                        throw new IOException(failure);
                    }
                }
                if (o.readable() == 0) {
                    o.close();
                    continue;
                }
                buffer = o;
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        return true;
    }

    @Override
    public void allowDiscard() {
    }

    @Override
    public void cancelInput() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        publisherAsBlocking.close();
    }
}
