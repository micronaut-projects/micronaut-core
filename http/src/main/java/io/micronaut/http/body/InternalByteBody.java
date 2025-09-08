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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Internal extensions of {@link ByteBody}.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Internal
public abstract class InternalByteBody implements ByteBody {
    /**
     * Variant of {@link #buffer()} that uses the {@link ExecutionFlow} API for extra efficiency.
     *
     * @return A flow that completes when all bytes are available
     */
    @NonNull
    public abstract ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow();

    @Override
    public final CompletableFuture<? extends CloseableAvailableByteBody> buffer() {
        return bufferFlow().toCompletableFuture();
    }

    @Override
    public @NonNull Publisher<byte[]> toByteArrayPublisher() {
        return Flux.from(toReadBufferPublisher())
            .doOnDiscard(ReadBuffer.class, ReadBuffer::close)
            .map(ReadBuffer::toArray);
    }

    @Override
    public abstract @NonNull Publisher<ReadBuffer> toReadBufferPublisher();

    public static ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow(ByteBody body) {
        if (body instanceof InternalByteBody internal) {
            return internal.bufferFlow();
        } else {
            return CompletableFutureExecutionFlow.just(body.buffer());
        }
    }
}
