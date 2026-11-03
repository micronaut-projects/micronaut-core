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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.PrimitiveTargetTypeConverter;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * The converters of string values to primitives of a conversion service, for the primitive accessors
 * of {@link io.micronaut.http.NamedValues}: the parsers that the converters the conversion service resolves
 * for {@link Argument#INT} and the other constants hold, which convert without the context of the value.
 * When the conversion service converts with another converter, e.g. one a registrar replaced the parser
 * with, there is none: the value converts with its context, which the converter may read and reject it in.
 * The default conversion service shares the resolved converters, so asking for them costs nothing per value;
 * another conversion service, a subclass of it included, is not asked: its values convert by boxing.
 *
 * @since 5.3.0
 */
@Internal
public final class PrimitiveValueConverters {

    /**
     * The length of the longest value a parser is tried with: a longer one, e.g. a malformed one an
     * attacker chose, converts like any value, so a value that does not parse is parsed once more only
     * when it is short.
     */
    public static final int MAX_PARSED_LENGTH = 32;

    private PrimitiveValueConverters() {
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code int} that parses it itself, or {@code null} if the conversion service converts with another converter, which is given the context of the value
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Integer> intConverter(ConversionService conversionService) {
        if (conversionService.getClass() != DefaultMutableConversionService.class) {
            // another conversion service resolves converters that box: not resolved for every value
            return null;
        }
        return conversionService.resolveConverter(String.class, Argument.INT) instanceof PrimitiveTargetTypeConverter<String, Integer> primitive ? primitive.primitiveParser() : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code long} that parses it itself, or {@code null} if the conversion service converts with another converter, which is given the context of the value
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Long> longConverter(ConversionService conversionService) {
        if (conversionService.getClass() != DefaultMutableConversionService.class) {
            // another conversion service resolves converters that box: not resolved for every value
            return null;
        }
        return conversionService.resolveConverter(String.class, Argument.LONG) instanceof PrimitiveTargetTypeConverter<String, Long> primitive ? primitive.primitiveParser() : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code double} that parses it itself, or {@code null} if the conversion service converts with another converter, which is given the context of the value
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Double> doubleConverter(ConversionService conversionService) {
        if (conversionService.getClass() != DefaultMutableConversionService.class) {
            // another conversion service resolves converters that box: not resolved for every value
            return null;
        }
        return conversionService.resolveConverter(String.class, Argument.DOUBLE) instanceof PrimitiveTargetTypeConverter<String, Double> primitive ? primitive.primitiveParser() : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code boolean} that parses it itself, or {@code null} if the conversion service converts with another converter, which is given the context of the value
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Boolean> booleanConverter(ConversionService conversionService) {
        if (conversionService.getClass() != DefaultMutableConversionService.class) {
            // another conversion service resolves converters that box: not resolved for every value
            return null;
        }
        return conversionService.resolveConverter(String.class, Argument.BOOLEAN) instanceof PrimitiveTargetTypeConverter<String, Boolean> primitive ? primitive.primitiveParser() : null;
    }
}
