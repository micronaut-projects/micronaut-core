/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.web.router.exceptions.UnsatisfiedPartRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link FormData} of a request.
 *
 * @param fields            The values of the text fields
 * @param files             The uploaded files
 * @param conversionService The conversion service of the route
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public record DefaultFormData(Map<String, List<String>> fields,
                       Map<String, List<CompletedFileUpload>> files,
                       ConversionService conversionService) implements FormData {

    @Override
    public Set<String> names() {
        return fields.keySet();
    }

    @Override
    public boolean contains(String name) {
        return fields.containsKey(name);
    }

    @Override
    public List<String> getValues(String name) {
        return fields.getOrDefault(name, List.of());
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Argument<T> argument = Argument.of(type, name);
        List<String> values = fields.get(name);
        if (values == null || values.isEmpty()) {
            throw new UnsatisfiedPartRouteException(name, argument);
        }
        return convert(argument, values.get(0));
    }

    @Override
    public <T> Optional<T> find(String name, Class<T> type) {
        List<String> values = fields.get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(convert(Argument.of(type, name), values.get(0)));
    }

    @Override
    public List<CompletedFileUpload> getFiles(String name) {
        return files.getOrDefault(name, List.of());
    }

    private <T> T convert(Argument<T> argument, Object value) {
        if (argument.getType().isInstance(value)) {
            return argument.getType().cast(value);
        }
        ConversionContext context = ConversionContext.of(argument);
        Optional<T> result = conversionService.convert(value, argument.getType(), context);
        if (result.isPresent()) {
            return result.get();
        }
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(argument, error.get());
        }
        throw UnsatisfiedRouteException.create(argument);
    }

    @Override
    public String toString() {
        return "FormData" + fields.keySet() + files.keySet();
    }
}
