/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.multipart;

import io.micronaut.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * <p>Represents a part of a {@link io.micronaut.http.MediaType#MULTIPART_FORM_DATA} request</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface FileUpload {

    /**
     * Gets the content of this part as an <tt>InputStream</tt>
     *
     * @return The content of this part as an <tt>InputStream</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    InputStream getInputStream() throws IOException;

    /**
     * Gets the content of this part as a <tt>byte[]</tt>
     *
     * @return The content of this part as a <tt>byte[]</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    byte[] getBytes() throws IOException;

    /**
     * Gets the content of this part as a <tt>ByteBuffer</tt>
     *
     * @return The content of this part as a <tt>ByteBuffer</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    ByteBuffer getByteBuffer() throws IOException;

    /**
     * Gets the content type of this part.
     *
     * @return The content type of this part.
     */
    Optional<MediaType> getContentType();

    /**
     * Gets the name of this part
     *
     * @return The name of this part
     */
    String getName();

    /**
     * Gets the name of this part
     *
     * @return The name of this part
     */
    String getFilename();

    /**
     * Returns the size of the part.
     *
     * @return The size of this part, in bytes.
     */
    long getSize();

    /**
     * Returns whether the {@link FileUpload} has been fully uploaded or is in a partial state
     *
     * @return True if the part is fully uploaded
     */
    boolean isComplete();
}
