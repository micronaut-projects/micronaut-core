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

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

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
        assertDiscardReleases(false);
    }

    /**
     * Same as {@link #discardingAnUnstartedStreamingBodyReleasesTheBuffer()}, but the buffered
     * backend response carries trailers, so that its available body is wrapped.
     */
    @Test
    void discardingAnUnstartedStreamingBodyWithTrailersReleasesTheBuffer() {
        assertDiscardReleases(true);
    }

    /**
     * The byte buffer publisher of an available body owns the buffer until it is delivered, e.g.
     * when a JSON stream of a buffered response is cancelled before the decoder asks for bytes.
     */
    @Test
    @SuppressWarnings("deprecation")
    void cancellingTheByteBufferPublisherOfAnAvailableBodyReleasesTheBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("response".getBytes(StandardCharsets.UTF_8));
            CloseableByteBody body = new NettyByteBodyFactory(channel).adapt(buf);
            Publisher<ByteBuffer<?>> publisher = body.toByteBufferPublisher();
            // an extra reference, so that a double release shows as a count of 0
            buf.retain();
            publisher.subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.cancel();
                }

                @Override
                public void onNext(ByteBuffer<?> byteBuffer) {
                    throw new AssertionError("Nothing was requested");
                }

                @Override
                public void onError(Throwable t) {
                    throw new AssertionError(t);
                }

                @Override
                public void onComplete() {
                    throw new AssertionError("Nothing was requested");
                }
            });
            assertEquals(1, buf.refCnt(), "the cancelled publisher did not release its buffer exactly once");
            buf.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void assertDiscardReleases(boolean trailers) {
        // the backend response lives on the client's channel, the relayed response on the server's
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel serverChannel = new EmbeddedChannel();
        try {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("backend response".getBytes(StandardCharsets.UTF_8));
            // an extra reference, so that a double release shows as a count of 0, not as an
            // IllegalReferenceCountException swallowed by the discard
            buf.retain();
            // the client buffers the backend response (Http1ResponseHandler.BufferedContent)
            NettyByteBodyFactory clientFactory = new NettyByteBodyFactory(clientChannel);
            CloseableByteBody backendResponse = clientFactory.adapt(buf);
            if (trailers) {
                backendResponse = clientFactory.withTrailers(backendResponse, new DefaultHttpHeaders().add("x-trailer", "value"));
            }
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
            assertEquals(1, buf.refCnt(), "discarded streaming body did not release its buffer exactly once");
            buf.release();
        } finally {
            clientChannel.finishAndReleaseAll();
            serverChannel.finishAndReleaseAll();
        }
    }
}
