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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

/**
 * Adapter from {@link Publisher} of NIO {@link ByteBuffer} to a {@link ReactiveByteBufferByteBody}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 * @deprecated Use {@link ByteBodyFactory#adapt}
 */
@Deprecated(forRemoval = true)
@Experimental
public final class ByteBufferBodyAdapter {
    private static @NonNull ByteBodyFactory byteBodyFactory() {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    }

    private static @NonNull Flux<ReadBuffer> toReadBuffers(@NonNull Publisher<ByteBuffer> source) {
        return Flux.from(source).map(ReadBufferFactory.getJdkFactory()::adapt);
    }

    /**
     * Create a new body that contains the bytes of the given publisher.
     *
     * @param source The byte publisher
     * @return A body with those bytes
     */
    @NonNull
    static ReactiveByteBufferByteBody adapt(@NonNull Publisher<ByteBuffer> source) {
        ByteBodyFactory bbf = byteBodyFactory();
        return (ReactiveByteBufferByteBody) bbf.adapt(toReadBuffers(source));
    }

    /**
     * Create a new body from the given publisher.
     *
     * @param publisher     The input publisher
     * @param contentLength Optional length of the body, must match the publisher exactly
     * @return The ByteBody fed by the publisher
     */
    public static CloseableByteBody adapt(@NonNull Publisher<ByteBuffer> publisher, @NonNull OptionalLong contentLength) {
        ByteBodyFactory bbf = byteBodyFactory();
        return bbf.adapt(toReadBuffers(publisher), contentLength);
    }
}
