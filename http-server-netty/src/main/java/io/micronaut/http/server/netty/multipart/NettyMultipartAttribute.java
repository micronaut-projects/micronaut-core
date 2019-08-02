package io.micronaut.http.server.netty.multipart;

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

public class NettyMultipartAttribute implements CompletedPart {

    private final Attribute attribute;

    public NettyMultipartAttribute(Attribute attribute) {
        this.attribute = attribute;
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(attribute.getByteBuf(), false);
    }

    @Override
    public byte[] getBytes() throws IOException {
        return ByteBufUtil.getBytes(attribute.getByteBuf());
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        return attribute.getByteBuf().nioBuffer();
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.empty();
    }
}
