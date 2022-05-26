/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;

import java.util.Optional;

/**
 * Exception handler interface that converts json binding exceptions to more specific {@link ConversionErrorException}s.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Internal
public interface JsonBeanPropertyBinderExceptionHandler {
    /**
     * Attempt to convert the given exception to a {@link ConversionErrorException}.
     *
     * @param object The object that was supposed to be updated, or {@code null}.
     * @param e      The exception that occurred during mapping.
     * @return The conversion error, or an empty value if default handling should be used.
     */
    Optional<ConversionErrorException> toConversionError(@Nullable Object object, @NonNull Exception e);
}
