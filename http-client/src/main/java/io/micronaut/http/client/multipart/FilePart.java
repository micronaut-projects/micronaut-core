package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;

/**
 * A class representing a File object in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class FilePart extends AbstractFilePart {

    private final File data;

    /**
     *
     * @param name Parameter name to bind in the multipart request
     * @param filename Name of the file
     * @param data The file to copy the content from
     */
    FilePart(String name, String filename, File data) {
        this(name, filename, null, data);
    }

    /**
     *
     * @param name Parameter name to bind in the multipart request
     * @param filename Name of the file
     * @param contentType The type of the content, example - "application/json", "text/plain" etc
     * @param data The file to copy the content from
     */
    FilePart(String name, String filename, MediaType contentType, File data) {
        super(name, filename, contentType);
        this.data = data;
    }

    /**
     * Copy the file content into {@link FileUpload}
     *
     * @see AbstractFilePart#setContent(FileUpload)
     */
    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(data);
    }

    /**
     *
     * @see AbstractFilePart#getLength()
     */
    @Override
    long getLength() {
        return data.length();
    }
}
