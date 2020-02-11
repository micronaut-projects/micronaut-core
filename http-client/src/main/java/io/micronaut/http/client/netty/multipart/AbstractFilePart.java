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
package io.micronaut.http.client.netty.multipart;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * The base class used by a {@link FilePart}, {@link BytePart}, & {@link InputStreamPart} to build a Netty multipart
 * request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
abstract class AbstractFilePart extends Part {
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
     * Copy the content into {@link FileUpload} object.
     *
     * @param fileUpload The {@link FileUpload} to write the content to
     * @throws IOException if there is an error writing the file
     */
    abstract void setContent(FileUpload fileUpload) throws IOException;

    /**
     * @return The size of the content to pass to {@link HttpDataFactory} in order to create {@link FileUpload} object
     */
    abstract long getLength();

    /**
     * Create an object of {@link InterfaceHttpData} to build Netty multipart request.
     *
     * @see Part#getData(HttpRequest, HttpDataFactory)
     */
    @Override
    InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
        MediaType mediaType = contentType;
        String contentType = mediaType.toString();
        String encoding = mediaType.isTextBased() ? null : "binary";
        Charset charset = mediaType.getCharset().orElse(null);
        FileUpload fileUpload = factory.createFileUpload(request, name, filename, contentType,
            encoding, charset, getLength());
        try {
            setContent(fileUpload);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return fileUpload;
    }
}
