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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * {@link io.micronaut.http.body.ByteBody} implementation that reads from an InputStream.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Experimental
public final class InputStreamByteBody implements CloseableByteBody, InternalByteBody {
    // originally from micronaut-servlet

    private final Context context;
    private ExtendedInputStream stream;

    private InputStreamByteBody(Context context, ExtendedInputStream stream) {
        this.context = context;
        this.stream = stream;
    }

    /**
     * Create a new stream-based {@link CloseableByteBody}. Ownership of the stream is transferred
     * to the returned body.
     *
     * @param stream The stream backing the body
     * @param length The expected content length (see {@link #expectedLength()})
     * @param ioExecutor An executor where blocking {@link InputStream#read()} may be performed
     * @param bufferFactory A {@link ByteBufferFactory} for buffer-based methods
     * @return The body
     */
    @NonNull
    public static CloseableByteBody create(@NonNull InputStream stream, @NonNull OptionalLong length, @NonNull Executor ioExecutor, @NonNull ByteBufferFactory<?, ?> bufferFactory) {
        ArgumentUtils.requireNonNull("stream", stream);
        ArgumentUtils.requireNonNull("length", length);
        ArgumentUtils.requireNonNull("ioExecutor", ioExecutor);
        ArgumentUtils.requireNonNull("bufferFactory", bufferFactory);
        return new InputStreamByteBody(new Context(length, ioExecutor, bufferFactory), ExtendedInputStream.wrap(stream));
    }

    @Override
    public @NonNull CloseableByteBody allowDiscard() {
        if (stream == null) {
            BaseSharedBuffer.failClaim();
        }
        stream.allowDiscard();
        return this;
    }

    @Override
    public void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    @Override
    public @NonNull CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        if (stream == null) {
            BaseSharedBuffer.failClaim();
        }
        StreamPair.Pair pair = StreamPair.createStreamPair(stream, backpressureMode);
        stream = pair.left();
        return new InputStreamByteBody(context, pair.right());
    }

    @Override
    public @NonNull OptionalLong expectedLength() {
        return context.expectedLength();
    }

    @Override
    public @NonNull ExtendedInputStream toInputStream() {
        ExtendedInputStream s = stream;
        if (s == null) {
            BaseSharedBuffer.failClaim();
        }
        stream = null;
        BaseSharedBuffer.logClaim();
        return s;
    }

    @Override
    public @NonNull Flux<byte[]> toByteArrayPublisher() {
        ExtendedInputStream s = toInputStream();
        Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();
        return sink.asFlux()
            .doOnRequest(req -> {
                long remaining = req;
                while (remaining > 0) {
                    byte @Nullable [] arr;
                    try {
                        arr = s.readSome();
                    } catch (IOException e) {
                        sink.tryEmitError(e);
                        break;
                    }
                    if (arr == null) {
                        sink.tryEmitComplete();
                        break;
                    } else {
                        remaining--;
                        sink.tryEmitNext(arr);
                    }
                }
            })
            .doOnTerminate(s::close)
            .doOnCancel(s::close)
            .subscribeOn(Schedulers.fromExecutor(context.ioExecutor()));
    }

    @Override
    public @NonNull Publisher<ByteBuffer<?>> toByteBufferPublisher() {
        return toByteArrayPublisher().map(context.bufferFactory::wrap);
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        ExtendedInputStream s = toInputStream();
        return ExecutionFlow.async(context.ioExecutor, () -> {
            try (ExtendedInputStream t = s) {
                return ExecutionFlow.just(AvailableByteArrayBody.create(context.bufferFactory(), t.readAllBytes()));
            } catch (Exception e) {
                return ExecutionFlow.error(e);
            }
        });
    }

    @Override
    public @NonNull CloseableByteBody move() {
        return new InputStreamByteBody(context, toInputStream());
    }

    private record Context(
        OptionalLong expectedLength,
        Executor ioExecutor,
        ByteBufferFactory<?, ?> bufferFactory
    ) {
    }
}
