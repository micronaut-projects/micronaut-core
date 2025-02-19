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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;

/**
 * A body reader {@link MessageBodyReader} with a type argument.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 4.6
 */
@Experimental
public interface TypedMessageBodyReader<T> extends MessageBodyReader<T> {

    /**
     * @return The body type.
     */
    @NonNull
    Argument<T> getType();

    @Override
    default boolean isReadable(Argument<T> type, MediaType mediaType) {
        return type.isAssignableFrom(getType()) && !type.getType().equals(Object.class);
    }
}
