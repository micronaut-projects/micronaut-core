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
package io.micronaut.http.server.cors;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 * Responsible for converting a map of configuration to an instance of {@link CorsOriginConfiguration}.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class CorsOriginConverter implements TypeConverter<Object, CorsOriginConfiguration> {

    private static final String ALLOWED_ORIGINS = "allowedOrigins";
    private static final String ALLOWED_METHODS = "allowedMethods";
    private static final String ALLOWED_HEADERS = "allowedHeaders";
    private static final String EXPOSED_HEADERS = "exposedHeaders";
    private static final String ALLOW_CREDENTIALS = "allowCredentials";
    private static final String MAX_AGE = "maxAge";

    @Override
    public Optional<CorsOriginConfiguration> convert(Object object, Class<CorsOriginConfiguration> targetType, ConversionContext context) {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        if (object instanceof Map) {
            Map mapConfig = (Map) object;
            ConvertibleValues<Object> convertibleValues = new ConvertibleValuesMap<>(mapConfig);

            convertibleValues
                .get(ALLOWED_ORIGINS, Argument.listOf(String.class))
                .ifPresent(configuration::setAllowedOrigins);

            convertibleValues
                .get(ALLOWED_METHODS, Argument.listOf(HttpMethod.class))
                .ifPresent(configuration::setAllowedMethods);

            convertibleValues
                .get(ALLOWED_HEADERS, Argument.listOf(String.class))
                .ifPresent(configuration::setAllowedHeaders);

            convertibleValues
                .get(EXPOSED_HEADERS, Argument.listOf(String.class))
                .ifPresent(configuration::setExposedHeaders);

            convertibleValues
                .get(ALLOW_CREDENTIALS, Boolean.class)
                .ifPresent(configuration::setAllowCredentials);

            convertibleValues
                .get(MAX_AGE, Long.class)
                .ifPresent(configuration::setMaxAge);
        }
        return Optional.of(configuration);
    }
}
