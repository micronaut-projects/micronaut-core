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
package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * How a {@link StreamingNettyByteBody.SharedBuffer} hands its input to its streaming subscribers.
 */
class SharedBufferHandOffTest {

    private static final int PIECES = 200;

    /**
     * With a single subscriber and no reservation left, every input buffer is handed over as it
     * is, the final one with the completion too. The bytes are the same and every buffer is
     * released once the subscriber has released it.
     */
    @Test
    void aSingleSubscriberReceivesTheInputBuffersThemselves() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        StreamingNettyByteBody.SharedBuffer buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, bytesConsumed -> { });
        StreamingNettyByteBody root = new StreamingNettyByteBody(buffer);
        Recorder recorder = new Recorder();
        root.primary(recorder);

        List<ByteBuf> inputs = new ArrayList<>();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < PIECES; i++) {
            String piece = "{\"id\":" + i + "},";
            expected.append(piece);
            ByteBuf input = Unpooled.copiedBuffer(piece, StandardCharsets.UTF_8);
            inputs.add(input);
            buffer.add(NettyReadBufferFactory.of(channel.alloc()).adapt(input));
        }
        ByteBuf last = Unpooled.copiedBuffer("]", StandardCharsets.UTF_8);
        expected.append(']');
        inputs.add(last);
        buffer.addAndComplete(NettyReadBufferFactory.of(channel.alloc()).adapt(last));

        Assertions.assertEquals(List.of(), recorder.failures);
        Assertions.assertTrue(recorder.complete);
        Assertions.assertEquals(inputs.size(), recorder.received.size());
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            ByteBuf received = recorder.received.get(i);
            Assertions.assertSame(inputs.get(i), received);
            actual.append(received.toString(StandardCharsets.UTF_8));
            received.release();
        }
        Assertions.assertEquals(expected.toString(), actual.toString());
        for (ByteBuf input : inputs) {
            Assertions.assertEquals(0, input.refCnt());
        }
        Assertions.assertFalse(channel.finishAndReleaseAll());
    }

    /**
     * Two subscribers of a split body each receive all the bytes, those buffered before they
     * subscribed and those that arrive after, and every buffer is released once both have
     * released theirs.
     */
    @Test
    void splitSubscribersEachReceiveAllBytes() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        StreamingNettyByteBody.SharedBuffer buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, bytesConsumed -> { });
        StreamingNettyByteBody root = new StreamingNettyByteBody(buffer);
        List<ByteBuf> inputs = new ArrayList<>();

        ByteBuf early = Unpooled.copiedBuffer("early,", StandardCharsets.UTF_8);
        inputs.add(early);
        buffer.add(NettyReadBufferFactory.of(channel.alloc()).adapt(early));

        CloseableByteBody split = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        Recorder first = new Recorder();
        Recorder second = new Recorder();
        root.primary(first);

        StringBuilder expected = new StringBuilder("early,");
        for (int i = 0; i < PIECES; i++) {
            if (i == PIECES / 2) {
                // the second subscriber joins with half of the pieces buffered for it
                ((StreamingNettyByteBody) split).primary(second);
            }
            String piece = i + ",";
            expected.append(piece);
            ByteBuf input = Unpooled.copiedBuffer(piece, StandardCharsets.UTF_8);
            inputs.add(input);
            buffer.add(NettyReadBufferFactory.of(channel.alloc()).adapt(input));
        }
        ByteBuf last = Unpooled.copiedBuffer("end", StandardCharsets.UTF_8);
        expected.append("end");
        inputs.add(last);
        buffer.addAndComplete(NettyReadBufferFactory.of(channel.alloc()).adapt(last));

        for (Recorder recorder : List.of(first, second)) {
            Assertions.assertEquals(List.of(), recorder.failures);
            Assertions.assertTrue(recorder.complete);
            StringBuilder actual = new StringBuilder();
            for (ByteBuf received : recorder.received) {
                actual.append(received.toString(StandardCharsets.UTF_8));
                received.release();
            }
            Assertions.assertEquals(expected.toString(), actual.toString());
        }
        for (ByteBuf input : inputs) {
            Assertions.assertEquals(0, input.refCnt());
        }
        Assertions.assertFalse(channel.finishAndReleaseAll());
    }

    private static final class Recorder implements BufferConsumer {
        final List<ByteBuf> received = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        boolean complete;

        @Override
        public void add(ReadBuffer rb) {
            received.add(NettyReadBufferFactory.toByteBuf(rb));
        }

        @Override
        public void complete() {
            complete = true;
        }

        @Override
        public void error(Throwable e) {
            failures.add(e);
        }
    }
}
