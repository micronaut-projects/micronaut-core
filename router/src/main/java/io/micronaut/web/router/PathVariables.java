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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The path variables of the route a request matched, for handler functions:
 *
 * <pre>{@code
 * routes.GET("/items/{id}", request -> {
 *     long id = PathVariables.of(request).get("id", Long.class);
 *     return HttpResponse.ok(items.find(id));
 * });
 * }</pre>
 *
 * <p>Values convert with the conversion service of the route, like the path variable arguments
 * of a controller method, and fail the same way: a missing variable or a value that does not
 * convert is answered with 400.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class PathVariables {

    private static final PathVariables NONE = new PathVariables(Map.of(), ConversionService.SHARED);

    private final Map<String, Object> values;
    private final ConversionService conversionService;

    private PathVariables(Map<String, Object> values, ConversionService conversionService) {
        this.values = values;
        this.conversionService = conversionService;
    }

    /**
     * The path variables of the route the request matched.
     *
     * @param request The request
     * @return The path variables, empty if the request did not match a URI route
     */
    public static PathVariables of(HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request)
            .filter(UriRouteMatch.class::isInstance)
            .map(match -> new PathVariables(
                ((UriRouteMatch<?, ?>) match).getVariableValues(),
                match instanceof AbstractRouteMatch<?, ?> routeMatch ? routeMatch.conversionService : ConversionService.SHARED
            ))
            .orElse(NONE);
    }

    /**
     * @return The names of the variables that have a value
     */
    public Set<String> names() {
        return values.keySet();
    }

    /**
     * @param name The name of the variable
     * @return Whether the variable has a value
     */
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /**
     * A required variable.
     *
     * @param name The name of the variable
     * @return The value
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
     */
    public String get(String name) {
        return get(name, String.class);
    }

    /**
     * A required variable converted to a type.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    public <T> T get(String name, Class<T> type) {
        Argument<T> argument = Argument.of(type, name);
        Object value = values.get(name);
        if (value == null) {
            throw new UnsatisfiedPathVariableRouteException(name, argument);
        }
        return convert(argument, value);
    }

    /**
     * An optional variable.
     *
     * @param name The name of the variable
     * @return The value, if present
     */
    public Optional<String> find(String name) {
        return find(name, String.class);
    }

    /**
     * An optional variable converted to a type.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
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
