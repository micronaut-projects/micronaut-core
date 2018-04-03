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

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param data file content bytes
     */
    BytePart(String name, String filename, byte[] data) {
        this(name, filename, null, data);
    }

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param data content byte[]
     * @param contentType data content type
     */
    BytePart(String name, String filename, MediaType contentType, byte[] data) {
        super(name, filename, contentType);
        this.data = data;
    }

    /**
     * Copy the data content into {@link FileUpload}
     *
     * @param fileUpload an object of class extending {@link FileUpload}
     * @throws IOException
     */
    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(new ByteArrayInputStream(data));
    }

    /**
     *
     * @return length of file data
     */
    @Override
    long getLength() {
        return data.length;
    }
}
