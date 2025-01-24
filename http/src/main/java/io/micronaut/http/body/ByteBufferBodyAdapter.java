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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.reactivestreams.Publisher;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

/**
 * Adapter from {@link Publisher} of NIO {@link ByteBuffer} to a {@link ReactiveByteBufferByteBody}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Experimental
public final class ByteBufferBodyAdapter extends AbstractBodyAdapter<ByteBuffer, ReactiveByteBufferByteBody.SharedBuffer> {
    private ByteBufferBodyAdapter(Publisher<ByteBuffer> source, @Nullable Runnable onDiscard) {
        super(source, onDiscard);
    }

    /**
     * Create a new body that contains the bytes of the given publisher.
     *
     * @param source The byte publisher
     * @return A body with those bytes
     */
    @NonNull
    static ReactiveByteBufferByteBody adapt(@NonNull Publisher<ByteBuffer> source) {
        return adapt(source, null, null);
    }

    /**
     * Create a new body that contains the bytes of the given publisher.
     *
     * @param publisher        The byte publisher
     * @param headersForLength Optional headers for reading the {@code content-length} header
     * @param onDiscard        Optional runnable to call if the body is discarded ({@link #allowDiscard()})
     * @return A body with those bytes
     */
    @NonNull
    static ReactiveByteBufferByteBody adapt(@NonNull Publisher<ByteBuffer> publisher, @Nullable HttpHeaders headersForLength, @Nullable Runnable onDiscard) {
        ByteBufferBodyAdapter adapter = new ByteBufferBodyAdapter(publisher, onDiscard);
        adapter.sharedBuffer = new ReactiveByteBufferByteBody.SharedBuffer(BodySizeLimits.UNLIMITED, adapter);
        if (headersForLength != null) {
            adapter.sharedBuffer.setExpectedLengthFrom(headersForLength.get(HttpHeaders.CONTENT_LENGTH));
        }
        return new ReactiveByteBufferByteBody(adapter.sharedBuffer);
    }

    /**
     * Create a new body from the given publisher.
     *
     * @param publisher     The input publisher
     * @param contentLength Optional length of the body, must match the publisher exactly
     * @return The ByteBody fed by the publisher
     */
    public static CloseableByteBody adapt(@NonNull Publisher<ByteBuffer> publisher, @NonNull OptionalLong contentLength) {
        ByteBufferBodyAdapter adapter = new ByteBufferBodyAdapter(publisher, null);
        adapter.sharedBuffer = new ReactiveByteBufferByteBody.SharedBuffer(BodySizeLimits.UNLIMITED, adapter);
        contentLength.ifPresent(adapter.sharedBuffer::setExpectedLength);
        return new ReactiveByteBufferByteBody(adapter.sharedBuffer);
    }

    @Override
    public void onNext(ByteBuffer buffer) {
        long newDemand = demand.addAndGet(-buffer.remaining());
        sharedBuffer.add(buffer);
        if (newDemand > 0) {
            subscription.request(1);
        }
    }
}
