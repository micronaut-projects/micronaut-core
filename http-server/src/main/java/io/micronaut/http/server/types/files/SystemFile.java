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

import java.io.File;

/**
 * Used as the return value of a route execution to send {@link File} instances to the client. More efficient
 * than {@link StreamedFile}.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
public class SystemFile implements FileCustomizableResponseType {

    private final File file;
    private final MediaType mediaType;
    private String attachmentName;

    /**
     * @param file The file to respond with
     */
    public SystemFile(File file) {
        this.file = file;
        this.mediaType = MediaType.forFilename(file.getName());
    }

    /**
     * @param file The file to respond with
     * @param mediaType The content type of the response
     */
    public SystemFile(File file, MediaType mediaType) {
        this.file = file;
        this.mediaType = mediaType;
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public long getLength() {
        return file.length();
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return The file
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the file to be downloaded as an attachment.
     *
     * @return The same SystemFile instance
     */
    public SystemFile attach() {
        this.attachmentName = file.getName();
        return this;
    }

    /**
     * Sets the file to be downloaded as an attachment.
     *
     * @param attachmentName The name of the file to be attached.
     * @return The same SystemFile instance
     */
    public SystemFile attach(String attachmentName) {
        this.attachmentName = attachmentName;
        return this;
    }

    @Override
    public void process(MutableHttpResponse response) {
        if (attachmentName != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, String.format(ATTACHMENT_HEADER, attachmentName));
        }
    }
}
