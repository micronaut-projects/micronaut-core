/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.stream.PublisherAsBlocking;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * Transform a {@link PublisherAsBlocking} of buffers into a {@link InputStream}.
 *
 * @author Jonas Konrad
 * @since 4.2.0
 */
@Internal
public final class PublisherAsStream extends InputStream {
    private final PublisherAsBlocking<ByteBuf> publisherAsBlocking;
    private ByteBuf buffer;

    public PublisherAsStream(PublisherAsBlocking<ByteBuf> publisherAsBlocking) {
        this.publisherAsBlocking = publisherAsBlocking;
    }

    @Override
    public int read() throws IOException {
        byte[] arr = new byte[1];
        int n = read(arr);
        return n == -1 ? -1 : arr[0] & 0xff;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
        while (buffer == null) {
            try {
                ByteBuf o = publisherAsBlocking.take();
                if (o == null) {
                    Throwable failure = publisherAsBlocking.getFailure();
                    if (failure == null) {
                        return -1;
                    } else {
                        throw new IOException(failure);
                    }
                }
                if (o.readableBytes() == 0) {
                    o.release();
                    continue;
                }
                buffer = o;
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }

        int toRead = Math.min(len, buffer.readableBytes());
        buffer.readBytes(b, off, toRead);
        if (buffer.readableBytes() == 0) {
            buffer.release();
            buffer = null;
        }
        return toRead;
    }

    @Override
    public void close() throws IOException {
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
        publisherAsBlocking.close();
    }
}
