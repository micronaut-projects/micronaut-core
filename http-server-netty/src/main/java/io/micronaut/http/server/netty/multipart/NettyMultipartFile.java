package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.Part;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

public class NettyMultipartFile implements Part, io.micronaut.http.multipart.FileUpload {

    private final FileUpload fileUpload;
    private final NettyPartData partData;

    public NettyMultipartFile(FileUpload fileUpload) {
        this.fileUpload = fileUpload;
        this.partData = new NettyPartData(() -> Optional.of(MediaType.of((fileUpload.getContentType()))), fileUpload::getByteBuf);
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
