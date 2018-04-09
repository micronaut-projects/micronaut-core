package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

public class NettyPartData implements PartData {

    private final FileUpload fileUpload;
    private final Long chunk;

    public NettyPartData(FileUpload fileUpload, Long chunk) {
        this.fileUpload = fileUpload;
        this.chunk = chunk;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(getByteBuf(), true);
    }

    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(MediaType.of(fileUpload.getContentType()));
    }

    public ByteBuf getByteBuf() throws IOException {
        return fileUpload.getChunk(chunk.intValue());
    }
}
