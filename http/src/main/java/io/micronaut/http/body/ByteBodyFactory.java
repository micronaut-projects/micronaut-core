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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.OptionalLong;

/**
 * Factory methods for {@link ByteBody}s.
 * <p>While this is public API, extension is only allowed by micronaut-core.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@Experimental
public class ByteBodyFactory {
    private final ByteBufferFactory<?, ?> byteBufferFactory;
    private final ReadBufferFactory readBufferFactory;

    /**
     * Internal constructor.
     *
     * @param byteBufferFactory The buffer factory
     * @param readBufferFactory The read buffer factory
     */
    @Internal
    protected ByteBodyFactory(@NonNull ByteBufferFactory<?, ?> byteBufferFactory, ReadBufferFactory readBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
        this.readBufferFactory = readBufferFactory;
    }

    /**
     * Create a default body factory. Where possible, prefer using an existing factory that may
     * have runtime-specific optimizations, such as the factory passed to
     * {@link ResponseBodyWriter}.
     *
     * @param byteBufferFactory The base buffer factory
     * @return The body factory
     */
    @NonNull
    public static ByteBodyFactory createDefault(@NonNull ByteBufferFactory<?, ?> byteBufferFactory) {
        return new ByteBodyFactory(byteBufferFactory, ReadBufferFactory.getJdkFactory());
    }

    /**
     * Get the underlying {@link ByteBufferFactory}. Where possible, prefer using methods on the
     * body factory directly.
     *
     * @return The buffer factory
     * @deprecated Use {@link #readBufferFactory()}
     */
    @NonNull
    @Deprecated
    public final ByteBufferFactory<?, ?> byteBufferFactory() {
        return byteBufferFactory;
    }

    /**
     * Get the underlying {@link ReadBufferFactory}.
     *
     * @return The factory
     * @since 4.10.0
     */
    @NonNull
    public ReadBufferFactory readBufferFactory() {
        return readBufferFactory;
    }

    /**
     * Create a new {@link CloseableAvailableByteBody} from the given buffer. Ownership of the
     * buffer is transferred to this method; the original buffer may be copied or used as-is
     * depending on implementation. If the buffer is {@link ReferenceCounted}, release ownership is
     * also transferred to this method.
     *
     * @param buffer The buffer
     * @return A {@link ByteBody} with the same content as the buffer
     */
    @NonNull
    public CloseableAvailableByteBody adapt(@NonNull ByteBuffer<?> buffer) {
        return adapt(readBufferFactory.adapt(buffer));
    }

    /**
     * Create a new {@link CloseableAvailableByteBody} from the given array. Ownership of the array
     * is transferred to this method; the array may be copied or used as-is, so do not modify the
     * array after passing it to this method.
     *
     * @param array The array
     * @return A {@link ByteBody} with the same content as the array
     */
    @NonNull
    public CloseableAvailableByteBody adapt(byte @NonNull [] array) {
        return adapt(readBufferFactory().adapt(array));
    }

    /**
     * Create a new {@link CloseableAvailableByteBody} from the given buffer. Ownership of the
     * buffer is transferred to this method.
     *
     * @param readBuffer The buffer
     * @return A {@link ByteBody} with the same content as the buffer
     * @since 4.10.0
     */
    @NonNull
    public CloseableAvailableByteBody adapt(@NonNull ReadBuffer readBuffer) {
        return AvailableByteArrayBody.create(readBuffer);
    }

    /**
     * Buffer any data written to an {@link OutputStream} and return it as a {@link ByteBody}.
     *
     * @param writer The function that will write to the {@link OutputStream}
     * @return The data written to the stream
     * @param <T> Exception type thrown by the consumer
     * @throws T Exception thrown by the consumer
     */
    @NonNull
    public <T extends Throwable> CloseableAvailableByteBody buffer(@NonNull ThrowingConsumer<? super OutputStream, T> writer) throws T {
        return adapt(readBufferFactory().buffer(writer));
    }

    /**
     * Create an empty body.
     *
     * @return The empty body
     */
    @NonNull
    public CloseableAvailableByteBody createEmpty() {
        return adapt(readBufferFactory().createEmpty());
    }

    /**
     * Encode the given {@link CharSequence} and create a {@link ByteBody} from it.
     *
     * @param cs      The input string
     * @param charset The charset to use for encoding
     * @return The encoded body
     */
    @NonNull
    public CloseableAvailableByteBody copyOf(@NonNull CharSequence cs, @NonNull Charset charset) {
        return adapt(readBufferFactory().copyOf(cs, charset));
    }

    /**
     * Copy the data of the given {@link InputStream} into an available {@link ByteBody}. If the
     * input is blocking, this method will also block.
     *
     * @param stream The input to copy
     * @return A body containing the data read from the input
     * @throws IOException Any exception thrown by the {@link InputStream} read methods
     */
    @NonNull
    @Blocking
    public CloseableAvailableByteBody copyOf(@NonNull InputStream stream) throws IOException {
        return adapt(readBufferFactory().copyOf(stream));
    }

    /**
     * Create a new streaming body to push data into. <b>Internal API.</b>
     *
     * @param limits   The input limits
     * @param upstream The upstream for backpressure
     * @return The streaming body tuple
     */
    @Internal
    @NonNull
    public StreamingBody createStreamingBody(@NonNull BodySizeLimits limits, @NonNull BufferConsumer.Upstream upstream) {
        ReactiveByteBufferByteBody.SharedBuffer sb = new ReactiveByteBufferByteBody.SharedBuffer(this.readBufferFactory(), limits, upstream);
        return new StreamingBody(sb, new ReactiveByteBufferByteBody(sb));
    }

    /**
     * Create a new body adapter for transforming a publisher into a {@link ByteBody}. <b>Internal
     * API.</b>
     *
     * @param publisher The publisher to transform
     * @param onDiscard Optional runnable to run on {@link BufferConsumer.Upstream#allowDiscard()}
     * @return The adapter
     */
    @Internal
    protected AbstractBodyAdapter createBodyAdapter(@NonNull Publisher<ReadBuffer> publisher, @Nullable Runnable onDiscard) {
        return new AbstractBodyAdapter(publisher, onDiscard);
    }

    /**
     * Create a new {@link ByteBody} that streams the given input buffers.
     *
     * @param publisher The input buffer publisher
     * @return The combined {@link ByteBody}
     */
    @NonNull
    public CloseableByteBody adapt(@NonNull Publisher<ReadBuffer> publisher) {
        return adapt(publisher, BodySizeLimits.UNLIMITED, null, null);
    }

    /**
     * Create a new {@link ByteBody} that streams the given input buffers.
     *
     * @param publisher        The input buffer publisher
     * @param sizeLimits       The input size limit
     * @param headersForLength A {@link HttpHeaders} object to introspect for determining the
     *                         {@link ByteBody#expectedLength()}
     * @param onDiscard        An optional {@link Runnable} to run when the body is
     *                         {@link ByteBody#allowDiscard() discarded}
     * @return The combined {@link ByteBody}
     */
    @NonNull
    public CloseableByteBody adapt(@NonNull Publisher<ReadBuffer> publisher, @NonNull BodySizeLimits sizeLimits, @Nullable HttpHeaders headersForLength, @Nullable Runnable onDiscard) {
        AbstractBodyAdapter adapter = createBodyAdapter(publisher, onDiscard);
        StreamingBody sb = createStreamingBody(sizeLimits, adapter);
        adapter.setSharedBuffer(sb.sharedBuffer);
        if (headersForLength != null) {
            sb.sharedBuffer.setExpectedLengthFrom(headersForLength.get(HttpHeaders.CONTENT_LENGTH));
        }
        return sb.rootBody;
    }

    /**
     * Create a new {@link ByteBody} that streams the given input buffers.
     *
     * @param publisher     The input buffer publisher
     * @param contentLength The optional content length for {@link ByteBody#expectedLength()}
     * @return The combined {@link ByteBody}
     */
    @NonNull
    public CloseableByteBody adapt(@NonNull Publisher<ReadBuffer> publisher, @NonNull OptionalLong contentLength) {
        AbstractBodyAdapter adapter = createBodyAdapter(publisher, null);
        StreamingBody sb = createStreamingBody(BodySizeLimits.UNLIMITED, adapter);
        adapter.setSharedBuffer(sb.sharedBuffer);
        contentLength.ifPresent(sb.sharedBuffer::setExpectedLength);
        return sb.rootBody;
    }

    /**
     * Convert a {@link ByteBody} into a {@link BaseStreamingByteBody} with the same content.
     * <b>Internal API.</b>
     *
     * @param body The body to convert
     * @return A streaming body with the same content
     */
    @Internal
    public BaseStreamingByteBody<?> toStreaming(@NonNull ByteBody body) {
        if (body instanceof BaseStreamingByteBody<?> bsbb) {
            return bsbb;
        }
        AbstractBodyAdapter adapter = createBodyAdapter(body.toReadBufferPublisher(), null);
        StreamingBody sb = createStreamingBody(BodySizeLimits.UNLIMITED, adapter);
        adapter.setSharedBuffer(sb.sharedBuffer);
        body.expectedLength().ifPresent(sb.sharedBuffer::setExpectedLength);
        return sb.rootBody;
    }

    /**
     * Return type for {@link #createStreamingBody(BodySizeLimits, BufferConsumer.Upstream)}.
     * <b>Internal API.</b>
     *
     * @param sharedBuffer The shared buffer to write data to
     * @param rootBody     The root body to read data from
     */
    @Internal
    public record StreamingBody(BaseSharedBuffer sharedBuffer, BaseStreamingByteBody<?> rootBody) {
    }
}
