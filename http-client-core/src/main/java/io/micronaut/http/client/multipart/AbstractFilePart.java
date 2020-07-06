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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.MediaType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * The base class used by a {@link FilePart}, {@link BytePart}, & {@link InputStreamPart} to build a Netty multipart
 * request.
 *
 * @author Puneet Behl
 * @since 1.0
 * @param <D> the data type
 */
abstract class AbstractFilePart<D> extends Part<D> {
    protected final String filename;
    protected final MediaType contentType;

    /**
     * Constructor to create an object.
     *
     * @param name        Parameter name to bind in the multipart request
     * @param filename    Name of the file
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
     * @return The size of the content
     */
    abstract long getLength();

    @NonNull
    @Override
    <T> T getData(MultipartDataFactory<T> factory) {
        MediaType mediaType = contentType;
        String encoding = mediaType.isTextBased() ? null : "binary";
        Charset charset = mediaType.getCharset().orElse(null);
        T fileUpload = factory.createFileUpload(name, filename, mediaType,
                encoding, charset, getLength());
        try {
            factory.setContent(fileUpload, getContent());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return fileUpload;
    }
}
