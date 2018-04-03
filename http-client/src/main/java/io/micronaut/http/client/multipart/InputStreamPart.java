package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;

/**
 * A class representing a InputStream data {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class InputStreamPart extends AbstractFilePart {
    private final InputStream data;
    private final long contentLength;

    InputStreamPart(String name, String filename, InputStream data, long contentLength) {
        this(name, filename, null, data, contentLength);
    }

    InputStreamPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
        super(name, filename, contentType);
        this.data = data;
        this.contentLength = contentLength;
    }

    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(data);
    }

    @Override
    long getLength() {
        return contentLength;
    }
}
