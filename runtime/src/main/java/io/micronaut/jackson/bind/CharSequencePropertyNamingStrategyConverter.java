/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Optional;

/**
 * Type converter used to convert a CharSequence to a {@link PropertyNamingStrategy} instance.
 *
 * @author msanssouci
 * @since 1.1.0
 * @deprecated Replaced by {@link io.micronaut.jackson.convert.JacksonConverterRegistrar}
 */
@Deprecated
public class CharSequencePropertyNamingStrategyConverter implements TypeConverter<CharSequence, PropertyNamingStrategy> {

    @Override
    public Optional<PropertyNamingStrategy> convert(CharSequence charSequence, Class<PropertyNamingStrategy> targetType, ConversionContext context) {
        PropertyNamingStrategy propertyNamingStrategy = null;

        if (charSequence != null) {
            String stringValue = NameUtils.environmentName(charSequence.toString());

            if (StringUtils.isNotEmpty(stringValue)) {
                switch (stringValue) {
                    case "SNAKE_CASE":
                        propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE;
                        break;
                    case "UPPER_CAMEL_CASE":
                        propertyNamingStrategy = PropertyNamingStrategy.UPPER_CAMEL_CASE;
                        break;
                    case "LOWER_CASE":
                        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CASE;
                        break;
                    case "KEBAB_CASE":
                        propertyNamingStrategy = PropertyNamingStrategy.KEBAB_CASE;
                        break;
                    case "LOWER_CAMEL_CASE":
                        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;
                        break;
                    default:
                        break;
                }
            }
        }

        if (propertyNamingStrategy == null) {
            context.reject(charSequence, new IllegalArgumentException(String.format("Unable to convert '%s' to a com.fasterxml.jackson.databind.PropertyNamingStrategy", charSequence)));
        }

        return Optional.ofNullable(propertyNamingStrategy);
    }
}
