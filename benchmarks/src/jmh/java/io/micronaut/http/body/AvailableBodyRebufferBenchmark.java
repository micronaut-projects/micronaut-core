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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.OptionalLong;

/** Matched direct-transfer and publisher-adapter rebuffering fixtures. */
@Internal
@State(Scope.Thread)
public class AvailableBodyRebufferBenchmark {
    private static final ByteBodyFactory DIRECT = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final ByteBodyFactory BASELINE = new PublisherFactory();

    @Param({"0", "128", "8192"})
    public int size;

    @Benchmark
    public long directTransfer() {
        return rebuffer(DIRECT);
    }

    @Benchmark
    public long publisherAdapter() {
        return rebuffer(BASELINE);
    }

    private long rebuffer(ByteBodyFactory factory) {
        try (CloseableByteBody original = factory.adapt(new byte[size]);
             CloseableByteBody streaming = factory.toStreaming(original);
             CloseableAvailableByteBody available = InternalByteBody.bufferFlow(streaming).tryCompleteValue()) {
            return available.length();
        }
    }

    /** Previous path, with the independent length-before-claim bug already corrected. */
    @Internal
    private static final class PublisherFactory extends ByteBodyFactory {
        private PublisherFactory() {
            super(ByteArrayBufferFactory.INSTANCE, ReadBufferFactory.getJdkFactory());
        }

        @Override
        public BaseStreamingByteBody<?> toStreaming(ByteBody body) {
            OptionalLong expectedLength = body.expectedLength();
            AbstractBodyAdapter adapter = createBodyAdapter(body.toReadBufferPublisher(), null);
            StreamingBody streaming = createStreamingBody(BodySizeLimits.UNLIMITED, adapter);
            adapter.setSharedBuffer(streaming.sharedBuffer());
            adapter.setTrailers(body.trailers());
            expectedLength.ifPresent(streaming.sharedBuffer()::setExpectedLength);
            return streaming.rootBody();
        }
    }
}
