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

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param data file InputStream data
     * @param contentLength length of data
     */
    InputStreamPart(String name, String filename, InputStream data, long contentLength) {
        this(name, filename, null, data, contentLength);
    }

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param contentType content type of data in {@link InputStream}
     * @param data file InputStream data
     * @param contentLength length of data
     */
    InputStreamPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
        super(name, filename, contentType);
        this.data = data;
        this.contentLength = contentLength;
    }

    /**
     * Copy the {@link InputStream} data content into {@link FileUpload}
     *
     * @param fileUpload an object of class extending {@link FileUpload}
     * @throws IOException
     */
    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(data);
    }

    /**
     *
     * @return length of file data
     */
    @Override
    long getLength() {
        return contentLength;
    }
}
