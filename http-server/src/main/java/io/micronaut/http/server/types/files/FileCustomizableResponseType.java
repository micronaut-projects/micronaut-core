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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.CustomizableResponseType;

/**
 * A special type for file handling.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Experimental
public interface FileCustomizableResponseType extends CustomizableResponseType {

    String ATTACHMENT_HEADER = "attachment; filename=\"%s\"";

    /**
     * @return The last modified date of the file
     */
    long getLastModified();

    /**
     * @return The length of the file
     */
    long getLength();

    /**
     * @return The name of the file
     */
    @Deprecated
    String getName();

    /**
     * @return The media type of the file
     */
    MediaType getMediaType();
}
