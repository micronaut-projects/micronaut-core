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
 * Exception thrown when none of the produceable content types matches any of the accepted types.
 *
 * @since 4.6.0
 */
public class NotAcceptableException extends HttpStatusException {
    private final Collection<MediaType> acceptedTypes;
    private final Collection<MediaType> produceableContentTypes;

    /**
     *
     * @param acceptedTypes Accepted types as signaled in the Request
     * @param produceableContentTypes types that the server can produce
     */
    public NotAcceptableException(Collection<MediaType> acceptedTypes,
                                  Collection<MediaType> produceableContentTypes) {
        super(HttpStatus.NOT_ACCEPTABLE, "Specified Accept Types " + acceptedTypes + " not supported. Supported types: " + produceableContentTypes);
        this.acceptedTypes = acceptedTypes;
        this.produceableContentTypes = produceableContentTypes;
    }

    /**
     *
     * @return Accepted types as signaled in the Request
     */
    public Collection<MediaType> getAcceptedTypes() {
        return acceptedTypes;
    }

    /**
     *
     * @return types that the server can produce
     */
    public Collection<MediaType> getProduceableContentTypes() {
        return produceableContentTypes;
    }
}
