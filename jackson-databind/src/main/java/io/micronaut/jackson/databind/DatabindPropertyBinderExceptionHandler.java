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
package io.micronaut.jackson.databind;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.bind.JsonBeanPropertyBinderExceptionHandler;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * jackson-databind implementation of {@link JsonBeanPropertyBinderExceptionHandler}, handles
 * {@link InvalidFormatException}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Singleton
final class DatabindPropertyBinderExceptionHandler implements JsonBeanPropertyBinderExceptionHandler {
    @Override
    public Optional<ConversionErrorException> toConversionError(@Nullable Object object, @NonNull Exception e) {
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Object originalValue = ife.getValue();
            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.ofNullable(originalValue);
                }
            };
            Class<?> type = object != null ? object.getClass() : Object.class;
            List<JsonMappingException.Reference> path = ife.getPath();
            String name;
            if (!path.isEmpty()) {
                name = path.get(path.size() - 1).getFieldName();
            } else {
                name = NameUtils.decapitalize(type.getSimpleName());
            }
            return Optional.of(new ConversionErrorException(Argument.of(ife.getTargetType(), name), conversionError));
        }
        return Optional.empty();
    }
}
