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
package io.micronaut.http.server.types.files;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.types.CustomizableResponseTypeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

/**
 * A special type for streaming an {@link InputStream} representing a file or resource.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class StreamedFile implements FileCustomizableResponseType {

    private final MediaType mediaType;
    private final String name;
    private final long lastModified;
    private final InputStream inputStream;
    private final long length;
    private String attachmentName;

    /**
     * @param inputStream The input stream
     * @param mediaType   The media type of the content
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType) {
        this(inputStream, mediaType, Instant.now().toEpochMilli());
    }

    /**
     * @param inputStream  The input stream
     * @param mediaType    The media type of the content
     * @param lastModified The last modified date
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType, long lastModified) {
        this(inputStream, mediaType, lastModified, -1);
    }

    /**
     * @param inputStream   The input stream
     * @param mediaType     The media type of the content
     * @param lastModified  The last modified date
     * @param contentLength the content length
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType, long lastModified, long contentLength) {
        this.mediaType = mediaType;
        this.name = null;
        this.lastModified = lastModified;
        this.inputStream = inputStream;
        this.length = contentLength;
    }

    /**
     * Immediately opens a connection to the given URL to retrieve
     * data about the connection, including the input stream.
     *
     * @param url The URL to resource
     */
    public StreamedFile(URL url) {
        String path = url.getPath();
        int idx = path.lastIndexOf(File.separatorChar);
        this.name = idx > -1 ? path.substring(idx + 1) : path;
        this.mediaType = MediaType.forFilename(name);
        try {
            URLConnection con = url.openConnection();
            this.lastModified = con.getLastModified();
            this.inputStream = con.getInputStream();
            this.length = con.getContentLengthLong();
        } catch (IOException e) {
            throw new CustomizableResponseTypeException("Could not open a connection to the URL: " + path, e);
        }
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return The stream used to retrieve data for the file
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Sets the file to be downloaded as an attachment.
     *
     * @param attachmentName The name of the file to be attached.
     * @return The same StreamedFile instance
     */
    public StreamedFile attach(String attachmentName) {
        this.attachmentName = attachmentName;
        return this;
    }

    @Override
    public void process(MutableHttpResponse<?> response) {
        if (attachmentName != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, String.format(ATTACHMENT_HEADER, attachmentName));
        }
    }

}
