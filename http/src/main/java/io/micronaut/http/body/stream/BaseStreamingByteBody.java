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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;

/**
 * Implementation of streaming {@link io.micronaut.http.body.ByteBody}s based on a
 * {@link BaseSharedBuffer}.
 *
 * @param <SB> The shared buffer type
 * @since 4.10.0
 * @author Jonas Konrad
 */
@Internal
public abstract class BaseStreamingByteBody<SB extends BaseSharedBuffer> extends InternalByteBody implements CloseableByteBody {
    protected final SB sharedBuffer;
    protected BufferConsumer.Upstream upstream;

    protected BaseStreamingByteBody(SB sharedBuffer, BufferConsumer.Upstream upstream) {
        this.sharedBuffer = sharedBuffer;
        this.upstream = upstream;
    }

    @Override
    public final @NonNull OptionalLong expectedLength() {
        return sharedBuffer.getExpectedLength();
    }

    /**
     * Consume this buffer.
     *
     * @param primary The consumer or {@code null} to discard the data
     * @return The upstream to signal backpressure
     */
    @NonNull
    public abstract BufferConsumer.Upstream primary(@Nullable BufferConsumer primary);

    /**
     * Create a new body instance on the same shared buffer with the given upstream. This is used
     * for {@link #move()}.
     *
     * @param upstream The upstream
     * @return The body
     */
    @NonNull
    protected abstract BaseStreamingByteBody<SB> derive(@NonNull BufferConsumer.Upstream upstream);

    @Override
    public final @NonNull Publisher<ReadBuffer> toReadBufferPublisher() {
        BaseSharedBuffer.AsFlux asFlux = new BaseSharedBuffer.AsFlux(sharedBuffer);
        BufferConsumer.Upstream upstream = primary(asFlux);
        return asFlux.asFlux(upstream);
    }

    @Override
    public final @NonNull InputStream toInputStream() {
        PublisherAsBlocking publisherAsBlocking = new PublisherAsBlocking();
        toReadBufferPublisher().subscribe(publisherAsBlocking);
        return new PublisherAsStream(publisherAsBlocking);
    }

    @Override
    public final @NonNull CloseableByteBody move() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        recordPrimaryOp();
        this.upstream = null;
        return derive(upstream);
    }

    @Override
    public final @NonNull CloseableByteBody allowDiscard() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        upstream.allowDiscard();
        return this;
    }
}
