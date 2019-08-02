package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedPart;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

public class NettyMultipartFile implements CompletedPart, io.micronaut.http.multipart.FileUpload {

    private final FileUpload fileUpload;

    public NettyMultipartFile(FileUpload fileUpload) {
        this.fileUpload = fileUpload;
    }

    @Override
    public String getFilename() {
        return fileUpload.getFilename();
    }

    @Override
    public long getSize() {
        return fileUpload.length();
    }

    @Override
    public long getDefinedSize() {
        return fileUpload.definedLength();
    }

    @Override
    public boolean isComplete() {
        return fileUpload.isCompleted();
    }

    @Override
    public String getName() {
        return fileUpload.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(fileUpload.getByteBuf(), false);
    }

    @Override
    public byte[] getBytes() throws IOException {
        return ByteBufUtil.getBytes(fileUpload.getByteBuf());
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        return fileUpload.getByteBuf().nioBuffer();
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(MediaType.of((fileUpload.getContentType())));
    }
}
