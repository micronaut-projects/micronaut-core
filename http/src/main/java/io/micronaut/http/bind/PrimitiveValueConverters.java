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
import io.micronaut.core.convert.PrimitiveTargetTypeConverter;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * The converters of string values to primitives of a conversion service, for the primitive accessors
 * of {@link io.micronaut.http.NamedValues}: those that the conversion service resolves for
 * {@link Argument#INT} and the other constants, when they convert to primitives without boxing. The
 * default conversion service shares them, so asking for them costs nothing per value.
 *
 * @since 5.3.0
 */
@Internal
public final class PrimitiveValueConverters {

    private PrimitiveValueConverters() {
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code int}, or {@code null} if the conversion service does not convert without boxing
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Integer> intConverter(ConversionService conversionService) {
        return conversionService.resolveConverter(String.class, Argument.INT) instanceof PrimitiveTargetTypeConverter<String, Integer> primitive ? primitive : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code long}, or {@code null} if the conversion service does not convert without boxing
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Long> longConverter(ConversionService conversionService) {
        return conversionService.resolveConverter(String.class, Argument.LONG) instanceof PrimitiveTargetTypeConverter<String, Long> primitive ? primitive : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code double}, or {@code null} if the conversion service does not convert without boxing
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Double> doubleConverter(ConversionService conversionService) {
        return conversionService.resolveConverter(String.class, Argument.DOUBLE) instanceof PrimitiveTargetTypeConverter<String, Double> primitive ? primitive : null;
    }

    /**
     * @param conversionService The conversion service
     * @return The converter to {@code boolean}, or {@code null} if the conversion service does not convert without boxing
     */
    public static @Nullable PrimitiveTargetTypeConverter<String, Boolean> booleanConverter(ConversionService conversionService) {
        return conversionService.resolveConverter(String.class, Argument.BOOLEAN) instanceof PrimitiveTargetTypeConverter<String, Boolean> primitive ? primitive : null;
    }
}
