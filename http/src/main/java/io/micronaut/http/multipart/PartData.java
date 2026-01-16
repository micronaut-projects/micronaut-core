/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.multipart;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.MediaType;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Represents a chunk of data belonging to a part of a multipart request.
 *
 * @param fieldMetadata The field metadata (name, file name, etc.)
 * @param readBuffer The buffered part data
 * @author James Kleeh
 * @since 1.0
 */
public record PartData(FormFieldMetadata fieldMetadata, ReadBuffer readBuffer) implements Closeable {

    /**
     * Gets the content of this chunk as an {@code InputStream}.
     *
     * @return The content of this chunk as an {@code InputStream}
     */
    public InputStream getInputStream() {
        return readBuffer.toInputStream();
    }

    /**
     * Gets the content of this chunk as a {@code byte[]}.
     *
     * @return The content of this chunk as a {@code byte[]}
     */
    public byte[] getBytes() {
        return readBuffer.toArray();
    }

    /**
     * Gets the content of this chunk as a {@code ByteBuffer}.
     *
     * @return The content of this chunk as a {@code ByteBuffer}
     */
    public ByteBuffer getByteBuffer() {
        return ByteBuffer.wrap(getBytes());
    }

    /**
     * Gets the content type of this chunk.
     *
     * @return The content type of this chunk.
     */
    public Optional<MediaType> getContentType() {
        return Optional.ofNullable(fieldMetadata.mediaType());
    }

    @Override
    public void close() {
        readBuffer.close();
    }
}
