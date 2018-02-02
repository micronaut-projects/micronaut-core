/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.types.files;

import org.particleframework.http.HttpHeaders;
import org.particleframework.http.MutableHttpResponse;

import java.io.File;

/**
 * Used as the return value of a route execution to indicate
 * the given file should be downloaded by the client instead of
 * displayed.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class AttachedFile extends SystemFileSpecialType {

    private static final String headerValue = "attachment; filename=\"%s\"";

    private final String filename;
    private final String attachmentName;

    public AttachedFile(File file) {
        this(file, file.getName());
    }

    public AttachedFile(File file, String filename) {
        super(file);
        this.filename = file.getName();
        this.attachmentName = filename;
    }

    @Override
    public void process(MutableHttpResponse response) {
        response.header(HttpHeaders.CONTENT_DISPOSITION, String.format(headerValue, attachmentName));
    }

    @Override
    public String getName() {
        return filename;
    }
}
