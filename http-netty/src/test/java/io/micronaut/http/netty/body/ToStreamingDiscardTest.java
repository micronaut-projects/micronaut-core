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

import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Buffers handed to bodies must be released when the body is discarded.
 */
class ToStreamingDiscardTest {

    /**
     * A body converted by {@link NettyByteBodyFactory#toStreaming} and then discarded without
     * being started (the server does this when the client disconnected before the response was
     * written) must release the buffer it holds.
     */
    @Test
    void discardingAnUnstartedStreamingBodyReleasesTheBuffer() {
        // the backend response lives on the client's channel, the relayed response on the server's
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel serverChannel = new EmbeddedChannel();
        try {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("backend response".getBytes(StandardCharsets.UTF_8));
            // the client buffers the backend response (Http1ResponseHandler.BufferedContent)
            CloseableByteBody backendResponse = new NettyByteBodyFactory(clientChannel).adapt(buf);
            StreamingNettyByteBody relayed = new NettyByteBodyFactory(serverChannel).toStreaming(backendResponse);
            // PipeliningServerHandler claims the body when it queues the response...
            BufferConsumer.Upstream upstream = relayed.primary(new BufferConsumer() {
                @Override
                public void add(ReadBuffer rb) {
                    rb.close();
                }

                @Override
                public void complete() {
                    // not needed for this test
                }

                @Override
                public void error(Throwable e) {
                    // not needed for this test
                }
            });
            // ...and when the client went away before it was written, discards it unstarted
            upstream.allowDiscard();
            clientChannel.runPendingTasks();
            serverChannel.runPendingTasks();
            assertEquals(0, buf.refCnt(), "discarded streaming body leaked its buffer");
        } finally {
            clientChannel.finishAndReleaseAll();
            serverChannel.finishAndReleaseAll();
        }
    }
}
