package io.micronaut.http.server.stack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Read every outbound buffer as its own part, in order. Unlike
     * {@link #readAllOutboundContiguous} this keeps the boundaries the codec produced, so a caller
     * can deliver each part as a separate read.
     */
    static List<ByteBuf> readAllOutboundParts(EmbeddedChannel clientChannel) {
        List<ByteBuf> parts = new ArrayList<>();
        while (true) {
            ByteBuf part = clientChannel.readOutbound();
            if (part == null) {
                break;
            }
            parts.add(part);
        }
        return parts;
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
