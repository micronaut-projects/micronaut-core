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
package io.micronaut.http.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link PathVariables} of the variable values of a matched route, converted with a
 * conversion service like the {@link io.micronaut.http.annotation.PathVariable} arguments of a
 * controller method: a value of a {@code List} type is split the same way.
 *
 * <p>A missing variable fails with the exception of {@link #unsatisfied(String, Argument)}, which
 * the router overrides to fail with its own, and a value that does not convert with a
 * {@link ConversionErrorException}, both answered with 400.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public class MatchedPathVariables implements PathVariables {

    private final Map<String, Object> values;
    private final @Nullable Map<String, Object> rawValues;
    private final ConversionService conversionService;
    // the decoded variables of the raw view, whose exceptions it fails with
    private final @Nullable MatchedPathVariables decoded;
    private @Nullable MatchedPathVariables raw;

    /**
     * Variables whose values are their raw values, e.g. of a source that does not decode them.
     *
     * @param values            The variable values, viewed read-only: the map of the match is
     *                          shared with the other arguments bound from it
     * @param conversionService The conversion service of the route
     */
    public MatchedPathVariables(Map<String, Object> values, ConversionService conversionService) {
        this(values, values, conversionService);
    }

    /**
     * @param values            The decoded variable values, viewed read-only
     * @param rawValues         The values as they appear in the path, not percent-decoded, viewed read-only,
     *                          or {@code null} if the source does not have them: {@link #raw()} then fails
     * @param conversionService The conversion service of the route
     */
    public MatchedPathVariables(Map<String, Object> values, @Nullable Map<String, Object> rawValues, ConversionService conversionService) {
        this.values = Collections.unmodifiableMap(values);
        if (rawValues == null) {
            this.rawValues = null;
        } else {
            this.rawValues = rawValues == values ? this.values : Collections.unmodifiableMap(rawValues);
        }
        this.conversionService = conversionService;
        this.decoded = null;
    }

    private MatchedPathVariables(MatchedPathVariables decoded, Map<String, Object> rawValues) {
        this.values = rawValues;
        this.rawValues = rawValues;
        this.conversionService = decoded.conversionService;
        this.decoded = decoded;
        this.raw = this;
    }

    /**
     * @return The variable values, read-only
     */
    public final Map<String, Object> values() {
        return values;
    }

    /**
     * @return The conversion service of the route
     */
    public final ConversionService conversionService() {
        return conversionService;
    }

    /**
     * @return The values as they appear in the path, not percent-decoded, read-only, or
     * {@code null} if the source does not have them
     */
    public final @Nullable Map<String, Object> rawValues() {
        return rawValues;
    }

    @Override
    public final PathVariables raw() {
        MatchedPathVariables view = raw;
        if (view == null) {
            Map<String, Object> raws = rawValues;
            if (raws == null) {
                throw new UnsupportedOperationException("The raw values of the path variables are not available");
            }
            view = new MatchedPathVariables(this, raws);
            raw = view;
        }
        return view;
    }

    @Override
    public final Set<String> names() {
        return values.keySet();
    }

    @Override
    public final boolean contains(String name) {
        return values.containsKey(name);
    }

    @Override
    public final <T> T get(String name, Argument<T> type) {
        Argument<T> argument = named(name, type);
        Object value = values.get(name);
        if (value == null) {
            throw unsatisfied(name, argument);
        }
        return convert(argument, value);
    }

    @Override
    public final <T> Optional<T> find(String name, Argument<T> type) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(convert(named(name, type), value));
    }

    /**
     * The exception of a required variable that has no value, answered with 400.
     *
     * @param name     The name of the variable
     * @param argument The type, named after the variable
     * @return The exception
     */
    protected RuntimeException unsatisfied(String name, Argument<?> argument) {
        if (decoded != null) {
            return decoded.unsatisfied(name, argument);
        }
        return new UnsatisfiedArgumentException(argument, "Required PathVariable [" + name + "] not specified");
    }

    /**
     * The exception of a value that does not convert, without an error of the conversion,
     * answered with 400.
     *
     * @param argument The type, named after the variable
     * @return The exception
     */
    protected RuntimeException unconvertible(Argument<?> argument) {
        if (decoded != null) {
            return decoded.unconvertible(argument);
        }
        return new UnsatisfiedArgumentException(argument);
    }

    /**
     * The type, named after the variable for the errors of the conversion.
     */
    private static <T> Argument<T> named(String name, Argument<T> type) {
        return name.equals(type.getName()) ? type : Argument.of(type.getType(), name, type.getAnnotationMetadata(), type.getTypeParameters());
    }

    private <T> T convert(Argument<T> argument, Object value) {
        if (argument.getTypeParameters().length == 0 && argument.getType().isInstance(value)) {
            return argument.getType().cast(value);
        }
        ConversionContext context = ConversionContext.of(argument);
        Optional<T> result = conversionService.convert(value, argument.getType(), context);
        // checked first: a collection is converted from the elements that convert, and the
        // others are rejected, which is not the value that was asked for
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(argument, error.get());
        }
        if (result.isPresent()) {
            return result.get();
        }
        throw unconvertible(argument);
    }

    @Override
    public String toString() {
        return "PathVariables" + values;
    }
}
