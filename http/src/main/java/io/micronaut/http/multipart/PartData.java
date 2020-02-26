/*
 * Copyright 2017-2020 original authors
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
 * Represents a chunk of data belonging to a part of a multipart request.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface PartData {

    /**
     * Gets the content of this chunk as an <tt>InputStream</tt>.
     *
     * @return The content of this chunk as an <tt>InputStream</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    InputStream getInputStream() throws IOException;

    /**
     * Gets the content of this chunk as a <tt>byte[]</tt>.
     *
     * @return The content of this chunk as a <tt>byte[]</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    byte[] getBytes() throws IOException;

    /**
     * Gets the content of this chunk as a <tt>ByteBuffer</tt>.
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
