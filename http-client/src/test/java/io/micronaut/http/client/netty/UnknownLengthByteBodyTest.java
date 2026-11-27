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

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnknownLengthByteBodyTest {

    /**
     * A relayed response with a buffered upstream body that the server discards before it is
     * written (the downstream connection closed) must release the upstream buffer.
     */
    @Test
    void discardingAnUnstartedRelayedAvailableBodyReleasesTheBuffer() {
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel serverChannel = new EmbeddedChannel();
        try {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("upstream response".getBytes(StandardCharsets.UTF_8));
            // an extra reference, so that a double release shows as a count of 0, not as an
            // IllegalReferenceCountException swallowed by the discard
            buf.retain();
            // the client buffers the upstream response, and the proxy relays it without a length
            UnknownLengthByteBody relayed = new UnknownLengthByteBody(new NettyByteBodyFactory(clientChannel).adapt(buf));
            StreamingNettyByteBody streaming = new NettyByteBodyFactory(serverChannel).toStreaming(relayed);
            BufferConsumer.Upstream upstream = streaming.primary(new RecordingConsumer());
            // the server discards the response unstarted
            upstream.allowDiscard();
            clientChannel.runPendingTasks();
            serverChannel.runPendingTasks();
            assertEquals(1, buf.refCnt(), "discarded relayed body did not release its buffer exactly once");
            buf.release();
        } finally {
            clientChannel.finishAndReleaseAll();
            serverChannel.finishAndReleaseAll();
        }
    }

    @Test
    void startedRelayedAvailableBodyDeliversTheBytes() {
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel serverChannel = new EmbeddedChannel();
        try {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("upstream response".getBytes(StandardCharsets.UTF_8));
            UnknownLengthByteBody relayed = new UnknownLengthByteBody(new NettyByteBodyFactory(clientChannel).adapt(buf));
            StreamingNettyByteBody streaming = new NettyByteBodyFactory(serverChannel).toStreaming(relayed);
            RecordingConsumer consumer = new RecordingConsumer();
            BufferConsumer.Upstream upstream = streaming.primary(consumer);
            upstream.start();
            upstream.onBytesConsumed(Long.MAX_VALUE);
            clientChannel.runPendingTasks();
            serverChannel.runPendingTasks();
            assertEquals("upstream response", consumer.bytes.toString(StandardCharsets.UTF_8));
            assertEquals(true, consumer.complete);
            assertEquals(0, buf.refCnt());
        } finally {
            clientChannel.finishAndReleaseAll();
            serverChannel.finishAndReleaseAll();
        }
    }

    private static final class RecordingConsumer implements BufferConsumer {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean complete;

        @Override
        public void add(ReadBuffer rb) {
            bytes.writeBytes(rb.toArray());
        }

        @Override
        public void complete() {
            complete = true;
        }

        @Override
        public void error(Throwable e) {
            throw new AssertionError(e);
        }
    }
}
