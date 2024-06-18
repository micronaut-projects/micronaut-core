package io.micronaut.http.server.stack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

final class NettyUtil {
    static ByteBuf readAllOutboundContiguous(EmbeddedChannel clientChannel) {
        ByteBuf requestBytes = PooledByteBufAllocator.DEFAULT.buffer();
        while (true) {
            ByteBuf part = clientChannel.readOutbound();
            if (part == null) {
                break;
            }
            requestBytes.writeBytes(part);
        }
        return requestBytes;
    }

    static ByteBuf readAllOutboundComposite(EmbeddedChannel channel) {
        CompositeByteBuf response = PooledByteBufAllocator.DEFAULT.compositeBuffer();
        while (true) {
            ByteBuf part = channel.readOutbound();
            if (part == null) {
                break;
            }
            response.addComponent(true, part);
        }
        return response;
    }
}
