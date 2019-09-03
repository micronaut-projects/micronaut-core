/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.FileUpload;

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
     * @param name     Parameter name to bind in the multipart request
     * @param filename Name of the file
     * @param data     The content to copy in {@link FileUpload}
     */
    BytePart(String name, String filename, byte[] data) {
        this(name, filename, null, data);
    }

    /**
     * @param name        Parameter name to bind in the multipart request
     * @param filename    Name of the file
     * @param contentType The type of the content, example - "application/json", "text/plain" etc
     * @param data        The content to copy in {@link FileUpload}
     */
    BytePart(String name, String filename, MediaType contentType, byte[] data) {
        super(name, filename, contentType);
        this.data = data;
    }

    /**
     * Copy the byte data into {@link FileUpload} object.
     *
     * @see AbstractFilePart#setContent(FileUpload)
     */
    @Override
    void setContent(FileUpload fileUpload) throws IOException {
        fileUpload.setContent(Unpooled.wrappedBuffer(data));
    }

    /**
     * @see AbstractFilePart#getLength()
     */
    @Override
    long getLength() {
        return data.length;
    }
}
