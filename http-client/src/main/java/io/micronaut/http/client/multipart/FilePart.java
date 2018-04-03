package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;

/**
 * A class representing a File object {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class FilePart extends AbstractFilePart {

    private final File data;

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param data file object
     */
    FilePart(String name, String filename, File data) {
        this(name, filename, null, data);
    }

    /**
     *
     * @param name parameter name
     * @param filename name of the file
     * @param contentType file content type
     * @param data file object
     */
    FilePart(String name, String filename, MediaType contentType, File data) {
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
        fileUpload.setContent(data);
    }

    /**
     *
     * @return length of file data
     */
    @Override
    long getLength() {
        return data.length();
    }
}
