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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a match for an error.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ErrorRouteMatch<T, R> extends AbstractRouteMatch<T, R> {

    private final Throwable error;
    private final Map<String, Object> variables;

    /**
     * @param error             The throwable
     * @param routeInfo         The route info
     * @param conversionService The conversion service
     */
    ErrorRouteMatch(Throwable error, ErrorRouteInfo<T, R> routeInfo, ConversionService conversionService) {
        super(routeInfo, conversionService);
        this.error = error;
        this.variables = new LinkedHashMap<>();
        for (Argument<?> argument : getArguments()) {
            if (argument.getType().isInstance(error)) {
                variables.put(argument.getName(), error);
            }
        }
    }

    @Override
    public Collection<Argument<?>> getRequiredArguments() {
        Argument<?>[] arguments = getArguments();
        var list = new ArrayList<Argument<?>>(arguments.length);
        for (Argument<?> argument : arguments) {
            if (!argument.getType().isInstance(error)) {
                list.add(argument);
            }
        }
        return list;
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return variables;
    }

    @Override
    public String toString() {
        return routeInfo.toString();
    }
}
