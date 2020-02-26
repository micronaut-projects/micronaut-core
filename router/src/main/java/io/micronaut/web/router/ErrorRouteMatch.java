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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a match for an error.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ErrorRouteMatch<T, R> extends StatusRouteMatch<T, R> {

    private final Throwable error;
    private final Map<String, Object> variables;

    /**
     * @param error The throwable
     * @param abstractRoute The abstract route
     * @param conversionService The conversion service
     */
    ErrorRouteMatch(Throwable error, DefaultRouteBuilder.AbstractRoute abstractRoute, ConversionService<?> conversionService) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, abstractRoute, conversionService);
        this.error = error;
        this.variables = new LinkedHashMap<>();
        for (Argument argument : getArguments()) {
            if (argument.getType().isInstance(error)) {
                variables.put(argument.getName(), error);
            }
        }
    }

    @Override
    public Collection<Argument> getRequiredArguments() {
        return Arrays
            .stream(getArguments())
            .filter(argument -> !argument.getType().isInstance(error))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return variables;
    }

    @Override
    public String toString() {
        return abstractRoute.toString();
    }
}
