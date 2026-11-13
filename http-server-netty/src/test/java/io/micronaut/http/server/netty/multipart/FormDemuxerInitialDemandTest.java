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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data that the body has buffered before the {@link FormDemuxer} is created reaches the demuxer
 * while it subscribes, before it knows its upstream. That data has to be acknowledged once the
 * upstream is known: the upstream only reads more after it is, so a form whose buffered start ends
 * between two parts would never continue.
 */
class FormDemuxerInitialDemandTest {

    @Test
    void dataBufferedBeforeTheDemuxerIsAcknowledged() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory byteBodyFactory = new NettyByteBodyFactory(channel);
        String boundary = "boundary1234";
        // one complete part, then the start of the headers of the next: no part is open
        String start = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"a\"\r\n"
            + "\r\n"
            + "x\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; na";

        CountingUpstream upstream = new CountingUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        streamingBody.sharedBuffer().add(byteBodyFactory.readBufferFactory().copyOf(start, StandardCharsets.UTF_8));

        new FormDemuxer(PostBodyDecoder.builder().forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody())
            .fields().subscribe(field -> field.close(), _ -> {
            });

        assertEquals(start.length(), upstream.consumed);

        // end the body, so that the demuxer releases what it holds
        streamingBody.sharedBuffer().error(new EOFException());
        channel.finishAndReleaseAll();
    }

    @Test
    void dataBufferedBeforeTheDemuxerThatFailsTheFormLetsTheRestBeDiscarded() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory byteBodyFactory = new NettyByteBodyFactory(channel);
        // a second field over a limit of one: the form fails while the demuxer subscribes, before
        // it knows its upstream
        String start = "foo=a&bar=b&baz=";

        CountingUpstream upstream = new CountingUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        streamingBody.sharedBuffer().add(byteBodyFactory.readBufferFactory().copyOf(start, StandardCharsets.UTF_8));

        List<Throwable> errors = new ArrayList<>();
        new FormDemuxer(PostBodyDecoder.builder().maxFields(1).forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody())
            .fields().subscribe(field -> field.close(), errors::add);

        assertEquals(1, errors.size());
        // the rest of the body is not read by anyone: the upstream may drop it, so that the
        // connection can go on with the error response
        assertTrue(upstream.allowDiscard);

        streamingBody.sharedBuffer().complete();
        channel.finishAndReleaseAll();
    }

    private static final class CountingUpstream implements BufferConsumer.Upstream {
        long consumed = 0;
        boolean allowDiscard = false;

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            consumed = Math.addExact(bytesConsumed, consumed);
        }

        @Override
        public void allowDiscard() {
            allowDiscard = true;
        }
    }
}
