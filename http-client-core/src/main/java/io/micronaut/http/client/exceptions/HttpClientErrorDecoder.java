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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Described;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.VndError;

import java.util.Optional;

/**
 * Strategy interface for decoding the error from a server respponse.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public interface HttpClientErrorDecoder {

    /**
     * The default implementation.
     */
    HttpClientErrorDecoder DEFAULT = new HttpClientErrorDecoder() { };

    /**
     * Default message decoder.
     *
     * @param error The error object
     * @return The message
     */
    default Optional<String> getMessage(Object error) {
        if (error == null) {
            return Optional.empty();
        }
        if (error instanceof JsonError) {
            return Optional.ofNullable(((JsonError) error).getMessage());
        } else {
            if (error instanceof Described) {
                return Optional.ofNullable(((Described) error).getDescription());
            } else {
                return Optional.of(error.toString());
            }
        }
    }

    /**
     * Gets the error type for the given media type.
     *
     * @param mediaType The media type
     * @return The error type
     */
    default Argument<?> getErrorType(MediaType mediaType) {
        if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
            return Argument.of(JsonError.class);
        } else if (mediaType.equals(MediaType.APPLICATION_VND_ERROR_TYPE)) {
            return Argument.of(VndError.class);
        } else {
            return Argument.STRING;
        }
    }
}
