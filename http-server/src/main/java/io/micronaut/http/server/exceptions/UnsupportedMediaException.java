/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.Collection;

/**
 * Exception thrown when the requested Content-Type is not supported.
 * @author Sergio del Amo
 * @since 4.6.0
 */
public class UnsupportedMediaException extends HttpStatusException {
    private final MediaType contentType;
    private final Collection<MediaType> acceptableContentTypes;

    /**
     *
     * @param contentType Requested Content Type
     * @param acceptableContentTypes Acceptable content types
     */
    public UnsupportedMediaException(MediaType contentType, Collection<MediaType> acceptableContentTypes) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content Type [" + contentType + "] not allowed. Allowed types: " + acceptableContentTypes);
        this.contentType = contentType;
        this.acceptableContentTypes = acceptableContentTypes;
    }

    /**
     *
     * @return Requested Content Type
     */
    public MediaType getContentType() {
        return contentType;
    }

    /**
     *
     * @return Acceptable content types
     */
    public Collection<MediaType> getAcceptableContentTypes() {
        return acceptableContentTypes;
    }
}
