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
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link PathVariables} of a route match: its variable values, converted with its
 * conversion service.
 *
 * @param values            The variable values
 * @param conversionService The conversion service of the route
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public record DefaultPathVariables(Map<String, Object> values, ConversionService conversionService) implements PathVariables {

    @Override
    public Set<String> names() {
        return values.keySet();
    }

    @Override
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Argument<T> argument = Argument.of(type, name);
        Object value = values.get(name);
        if (value == null) {
            throw new UnsatisfiedPathVariableRouteException(name, argument);
        }
        return convert(argument, value);
    }

    @Override
    public <T> Optional<T> find(String name, Class<T> type) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(convert(Argument.of(type, name), value));
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
        return values.toString();
    }
}
