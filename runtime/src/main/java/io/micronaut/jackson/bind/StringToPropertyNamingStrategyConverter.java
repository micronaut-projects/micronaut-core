/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.jackson.bind;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Type converter used to convert a String to a {@link PropertyNamingStrategy} instance.
 * {@link PropertyNamingStrategy#LOWER_CAMEL_CASE} is returned if an invalid String is provided.
 */
@Singleton
public class StringToPropertyNamingStrategyConverter implements TypeConverter<String, PropertyNamingStrategy> {

    @Override
    public Optional<PropertyNamingStrategy> convert(String string, Class<PropertyNamingStrategy> targetType, ConversionContext context) {
        Optional<PropertyNamingStrategy> convertedValue = Optional.empty();
        if (StringUtils.isNotEmpty(string)) {
            switch (string) {
                case "SNAKE_CASE":
                    convertedValue = Optional.of(PropertyNamingStrategy.SNAKE_CASE);
                    break;
                case "UPPER_CAMEL_CASE":
                    convertedValue = Optional.of(PropertyNamingStrategy.UPPER_CAMEL_CASE);
                    break;
                case "LOWER_CASE":
                    convertedValue = Optional.of(PropertyNamingStrategy.LOWER_CASE);
                    break;
                case "KEBAB_CASE":
                    convertedValue = Optional.of(PropertyNamingStrategy.KEBAB_CASE);
                    break;
                case "LOWER_CAMEL_CASE":
                    convertedValue = Optional.of(PropertyNamingStrategy.LOWER_CAMEL_CASE);
                    break;
                default:
                    break;
            }
        }

        if (!convertedValue.isPresent()) {
            throw new IllegalArgumentException(String.format("Unable to convert '%s' to a PropertyNamingStrategy", string));
        }

        return convertedValue;
    }
}
