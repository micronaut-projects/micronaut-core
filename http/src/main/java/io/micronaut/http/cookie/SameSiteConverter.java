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
package io.micronaut.http.cookie;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts a string to a {@link SameSite}.
 *
 * @author Sergio del Amo
 * @since 3.0.1
 */
@Singleton
public class SameSiteConverter implements TypeConverter<CharSequence, SameSite> {
    private static final Map<CharSequence, SameSite> CONVERSIONS = new ConcurrentHashMap<>();

    /**
     *
     * @param object     e.g. strict
     * @param targetType The target type being converted to {@link SameSite}
     * @param context    The {@link io.micronaut.core.convert.ConversionContext}
     * @return  {@link SameSite}
     */
    @Override
    public Optional<SameSite> convert(CharSequence object, Class<SameSite> targetType, ConversionContext context) {
        return Optional.ofNullable(CONVERSIONS.computeIfAbsent(object, charSequence -> {
            if (object == null) {
                return null;
            }
            try {
                return SameSite.valueOf(StringUtils.capitalize(object.toString().toLowerCase()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }));
    }
}
