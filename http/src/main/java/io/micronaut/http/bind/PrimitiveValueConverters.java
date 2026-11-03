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

/**
 * The converters of string values to primitives of a conversion service, resolved once and shared
 * by the values of the requests, e.g. by {@link MatchedPathVariables}.
 *
 * @param intConverter     The converter to {@code int}
 * @param longConverter    The converter to {@code long}
 * @param doubleConverter  The converter to {@code double}
 * @param booleanConverter The converter to {@code boolean}
 * @since 5.3.0
 */
@Internal
public record PrimitiveValueConverters(PrimitiveTargetTypeConverter<String, Integer> intConverter,
                                       PrimitiveTargetTypeConverter<String, Long> longConverter,
                                       PrimitiveTargetTypeConverter<String, Double> doubleConverter,
                                       PrimitiveTargetTypeConverter<String, Boolean> booleanConverter) {

    /**
     * Resolves the converters of the conversion service.
     *
     * @param conversionService The conversion service
     * @return The converters
     */
    public static PrimitiveValueConverters of(ConversionService conversionService) {
        return new PrimitiveValueConverters(
            PrimitiveTargetTypeConverter.of(conversionService.resolveConverter(String.class, Argument.INT)),
            PrimitiveTargetTypeConverter.of(conversionService.resolveConverter(String.class, Argument.LONG)),
            PrimitiveTargetTypeConverter.of(conversionService.resolveConverter(String.class, Argument.DOUBLE)),
            PrimitiveTargetTypeConverter.of(conversionService.resolveConverter(String.class, Argument.BOOLEAN))
        );
    }
}
