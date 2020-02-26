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
package io.micronaut.http.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;

import java.util.Optional;

/**
 * <p>Represents a part of a {@link io.micronaut.http.MediaType#MULTIPART_FORM_DATA} request.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface FileUpload {

    /**
     * Gets the content type of this part.
     *
     * @return The content type of this part.
     */
    Optional<MediaType> getContentType();

    /**
     * Gets the name of this part.
     *
     * @return The name of this part
     */
    String getName();

    /**
     * Gets the name of this part.
     *
     * @return The name of this part
     */
    String getFilename();

    /**
     * Returns the size of the part.
     *
     * @return The size of this part, in bytes.
     */
    long getSize();

    /**
     * Returns the defined content length of the part.
     *
     * @return The content length of this part, in bytes.
     */
    long getDefinedSize();

    /**
     * Returns whether the {@link FileUpload} has been fully uploaded or is in a partial state.
     *
     * @return True if the part is fully uploaded
     */
    boolean isComplete();
}
