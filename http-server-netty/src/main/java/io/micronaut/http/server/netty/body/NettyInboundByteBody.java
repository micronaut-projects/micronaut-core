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
package io.micronaut.http.server.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.InboundByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

@Internal
public abstract class NettyInboundByteBody implements InboundByteBody {
    protected static final Logger LOG = LoggerFactory.getLogger(NettyInboundByteBody.class);

    static void failClaim() {
        throw new IllegalStateException("Request body has already been claimed: Two conflicting sites are trying to access the request body. If this is intentional, the first user must InboundByteBody#split the body. To find out where the body was claimed, turn on TRACE logging for io.micronaut.http.server.netty.body.NettyInboundByteBody.");
    }

    protected abstract Flux<ByteBuf> toByteBufPublisher();

    public static Flux<ByteBuf> toByteBufs(InboundByteBody body) {
        if (body instanceof NettyInboundByteBody net) {
            return net.toByteBufPublisher();
        } else {
            return Flux.from(body.toByteArrayPublisher()).map(Unpooled::wrappedBuffer);
        }
    }

    @Override
    public @NonNull Publisher<byte[]> toByteArrayPublisher() {
        return toByteBufPublisher().map(bb -> {
            try {
                return ByteBufUtil.getBytes(bb);
            } finally {
                bb.release();
            }
        });
    }

    @Override
    public @NonNull Publisher<ByteBuffer<?>> toByteBufferPublisher() {
        return toByteBufPublisher().map(NettyByteBufferFactory.DEFAULT::wrap);
    }
}
