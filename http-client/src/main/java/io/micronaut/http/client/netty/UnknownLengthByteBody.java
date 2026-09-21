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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * A body that hides the {@link #expectedLength() length} of the body it wraps. The
 * {@link io.micronaut.http.client.ProxyHttpClient} relays upstream responses with it, so that the
 * server streams them without a {@code Content-Length}, as it did when the proxied body was a
 * content publisher.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class UnknownLengthByteBody implements CloseableByteBody {
    private final CloseableByteBody delegate;

    UnknownLengthByteBody(CloseableByteBody delegate) {
        this.delegate = delegate;
    }

    @Override
    public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        return new UnknownLengthByteBody(delegate.split(backpressureMode));
    }

    @Override
    public OptionalLong expectedLength() {
        return OptionalLong.empty();
    }

    @Override
    public InputStream toInputStream() {
        return delegate.toInputStream();
    }

    @Override
    public Publisher<byte[]> toByteArrayPublisher() {
        return delegate.toByteArrayPublisher();
    }

    @Override
    public Publisher<ReadBuffer> toReadBufferPublisher() {
        return delegate.toReadBufferPublisher();
    }

    @Override
    public CompletableFuture<? extends CloseableAvailableByteBody> buffer() {
        return delegate.buffer();
    }

    @Override
    public CloseableByteBody move() {
        return new UnknownLengthByteBody(delegate.move());
    }

    @Override
    public CloseableByteBody allowDiscard() {
        delegate.allowDiscard();
        return this;
    }

    @Override
    public void touch() {
        delegate.touch();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
