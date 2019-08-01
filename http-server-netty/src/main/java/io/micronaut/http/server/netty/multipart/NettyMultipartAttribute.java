package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.Part;
import io.netty.handler.codec.http.multipart.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

public class NettyMultipartAttribute implements Part {

    private final Attribute attribute;
    private final NettyPartData partData;

    public NettyMultipartAttribute(Attribute attribute) {
        this.attribute = attribute;
        this.partData = new NettyPartData(Optional::empty, attribute::getByteBuf);
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return partData.getInputStream();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return partData.getBytes();
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        return partData.getByteBuffer();
    }

    @Override
    public Optional<MediaType> getContentType() {
        return partData.getContentType();
    }
}
