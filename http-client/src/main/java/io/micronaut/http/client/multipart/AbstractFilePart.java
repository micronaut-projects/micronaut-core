package io.micronaut.http.client.multipart;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * The base class used by a {@link FilePart}, {@link BytePart}, & {@link InputStreamPart} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
abstract class AbstractFilePart extends Part {
    protected final String filename;
    protected final MediaType contentType;

    /**
     * Constructor to create an object
     *
     * @param name Parameter name to bind in the multipart request
     * @param filename Name of the file
     * @param contentType The type of the content, example - "application/json", "text/plain" etc
     */
    AbstractFilePart(String name, String filename, @Nullable MediaType contentType) {
        super(name);
        if (filename == null) {
            throw new IllegalArgumentException("Adding file parts with a null filename is not allowed");
        }
        this.filename = filename;
        if (contentType == null) {
            this.contentType = MediaType.forExtension(NameUtils.extension(filename)).orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        } else {
            this.contentType = contentType;
        }
    }

    /**
     * Copy the content into {@link FileUpload} object
     *
     * @param fileUpload The {@link FileUpload} to write the content to
     * @throws IOException
     */
    abstract void setContent(FileUpload fileUpload) throws IOException;

    /**
     *
     * @return The size of the content to pass to {@link HttpDataFactory} in order to create {@link FileUpload} object
     */
    abstract long getLength();

    /**
     * Create an object of {@link InterfaceHttpData} to build Netty multipart request
     *
     * @see Part#getData(HttpRequest, HttpDataFactory)
     */
    @Override
    InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
        MediaType mediaType = contentType;
        String contentType = mediaType.toString();
        String encoding = mediaType.isTextBased() ? null : "binary";

        FileUpload fileUpload = factory.createFileUpload(request, name, filename, contentType,
                encoding, null, getLength());
        try {
            setContent(fileUpload);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return fileUpload;
    }
}
