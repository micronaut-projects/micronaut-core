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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * A body with the bytes of another body and the given trailers. The trailers need the chunked
 * transfer coding on HTTP/1, so this body has no {@link #expectedLength() expected length} even
 * when the bytes have one.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class TrailingByteBody extends InternalByteBody implements CloseableByteBody {
    private final CloseableByteBody delegate;
    private final CompletionStage<HttpHeaders> trailers;

    /**
     * @param delegate The bytes
     * @param trailers The trailers
     */
    public TrailingByteBody(CloseableByteBody delegate, CompletionStage<HttpHeaders> trailers) {
        this.delegate = delegate;
        this.trailers = trailers;
    }

    @Override
    public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        return new TrailingByteBody(delegate.split(backpressureMode), trailers);
    }

    @Override
    public CloseableByteBody allowDiscard() {
        delegate.allowDiscard();
        return this;
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
    public ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        return InternalByteBody.bufferFlow(delegate);
    }

    @Override
    public CloseableByteBody move() {
        return new TrailingByteBody(delegate.move(), trailers);
    }

    @Override
    public CompletionStage<HttpHeaders> trailers() {
        return trailers;
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
