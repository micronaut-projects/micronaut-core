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
package io.micronaut.http.client.multipart;

import io.micronaut.http.MediaType;
import java.io.InputStream;

/**
 * A class representing a {@link InputStream} data in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class InputStreamPart extends AbstractFilePart<InputStream> {
    private final InputStream data;
    private final long contentLength;

    /**
     * @param name          Parameter name to bind in the multipart request
     * @param filename      Name of the file
     * @param data          The {@link InputStream} to copy the content from
     * @param contentLength The size of the content
     */
    InputStreamPart(String name, String filename, InputStream data, long contentLength) {
        this(name, filename, null, data, contentLength);
    }

    /**
     * @param name          Parameter name to bind in the multipart request
     * @param filename      Name of the file
     * @param contentType   The type of the content, example - "application/json", "text/plain" etc
     * @param data          The {@link InputStream} to copy the content from
     * @param contentLength The size of the content
     */
    InputStreamPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
        super(name, filename, contentType);
        this.data = data;
        this.contentLength = contentLength;
    }

    /**
     * @see AbstractFilePart#getLength()
     */
    @Override
    long getLength() {
        return contentLength;
    }

    @Override
    InputStream getContent() {
        return data;
    }
}
