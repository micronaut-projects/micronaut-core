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
package io.micronaut.http.server.types.files;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpResponse;

import java.io.File;

/**
 * Used as the return value of a route execution to indicate the given file should be downloaded by the client
 * instead of displayed.
 *
 * @deprecated Use {@link SystemFile instead}. This class conflates the source of the data with the type of
 * response being sent. This assumes the file should be sent as an attachment while {@link StreamedFile} does not.
 * The {@link SystemFile} class now behaves the same as {@link StreamedFile} by defaulting to inline with a method
 * to call to set it to be attached.
 *
 * <code>new AttachedFile(file, name) -> new SystemFile(file).attach(name)</code>
 * <code>new AttachedFile(file) -> new SystemFile(file).attach(file.getName())</code>
 *
 *
 * @author James Kleeh
 * @since 1.0
 */
@Deprecated
public class AttachedFile extends SystemFileCustomizableResponseType {

    private static final String HEADER_VALUE = "attachment; filename=\"%s\"";

    private final String filename;
    private final String attachmentName;
    private boolean inline;

    /**
     * @param file The file
     */
    public AttachedFile(File file) {
        this(file, file.getName());
    }

    /**
     * @param file     The file
     * @param filename The filename
     */
    public AttachedFile(File file, String filename) {
        super(file);
        this.filename = file.getName();
        this.attachmentName = filename;
    }

    /**
     * If called, the file will not be sent as an attachment.
     *
     * @return The same AttachedFile instance
     */
    public AttachedFile inline() {
        this.inline = true;
        return this;
    }

    @Override
    public void process(MutableHttpResponse response) {
        if (!inline) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, String.format(HEADER_VALUE, attachmentName));
        }
    }

    @Override
    public String getName() {
        return filename;
    }
}
