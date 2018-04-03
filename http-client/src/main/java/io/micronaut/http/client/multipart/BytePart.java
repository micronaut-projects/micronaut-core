package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * A class representing a byte[] data {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class BytePart extends AbstractFilePart {
    private final byte[] data;

    BytePart(String name, String filename, byte[] data) {
        this(name, filename, null, data);
    }

    BytePart(String name, String filename, MediaType contentType, byte[] data) {
        super(name, filename, contentType);
        this.data = data;
    }

    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(new ByteArrayInputStream(data));
    }

    @Override
    long getLength() {
        return data.length;
    }
}
