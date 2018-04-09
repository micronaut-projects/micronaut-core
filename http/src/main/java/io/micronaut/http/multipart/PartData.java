package io.micronaut.http.multipart;

import io.micronaut.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Represents a chunk of data belonging to a part of a multipart request
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface PartData {

    /**
     * Gets the content of this chunk as an <tt>InputStream</tt>
     *
     * @return The content of this chunk as an <tt>InputStream</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    InputStream getInputStream() throws IOException;

    /**
     * Gets the content of this chunk as a <tt>byte[]</tt>
     *
     * @return The content of this chunk as a <tt>byte[]</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    byte[] getBytes() throws IOException;

    /**
     * Gets the content of this chunk as a <tt>ByteBuffer</tt>
     *
     * @return The content of this chunk as a <tt>ByteBuffer</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    ByteBuffer getByteBuffer() throws IOException;

    /**
     * Gets the content type of this chunk.
     *
     * @return The content type of this chunk.
     */
    Optional<MediaType> getContentType();

}
