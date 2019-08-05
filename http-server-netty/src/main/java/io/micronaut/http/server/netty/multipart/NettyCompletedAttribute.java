package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.PartData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

@Internal
public class NettyCompletedAttribute implements CompletedPart {

    private final Attribute attribute;
    private final boolean controlRelease;

    public NettyCompletedAttribute(Attribute attribute) {
        this(attribute, true);
    }

    public NettyCompletedAttribute(Attribute attribute, boolean controlRelease) {
        this.attribute = attribute;
        this.controlRelease = controlRelease;
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(attribute.getByteBuf(), controlRelease);
    }

    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = attribute.getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            if (controlRelease) {
                byteBuf.release();
            }
        }
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = attribute.getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            if (controlRelease) {
                byteBuf.release();
            }
        }
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.empty();
    }
}
