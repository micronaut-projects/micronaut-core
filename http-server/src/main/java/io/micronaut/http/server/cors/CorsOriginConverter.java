/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;

import javax.inject.Singleton;
import java.util.List;
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

    private static final ArgumentConversionContext<List<HttpMethod>> CONVERSION_CONTEXT_LIST_OF_HTTP_METHOD = ConversionContext.of(Argument.listOf(HttpMethod.class));

    @Override
    public Optional<CorsOriginConfiguration> convert(Object object, Class<CorsOriginConfiguration> targetType, ConversionContext context) {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        if (object instanceof Map) {
            Map mapConfig = (Map) object;
            ConvertibleValues<Object> convertibleValues = new ConvertibleValuesMap<>(mapConfig);

            convertibleValues
                .get(ALLOWED_ORIGINS, ConversionContext.LIST_OF_STRING)
                .ifPresent(configuration::setAllowedOrigins);

            convertibleValues
                .get(ALLOWED_METHODS, CONVERSION_CONTEXT_LIST_OF_HTTP_METHOD)
                .ifPresent(configuration::setAllowedMethods);

            convertibleValues
                .get(ALLOWED_HEADERS, ConversionContext.LIST_OF_STRING)
                .ifPresent(configuration::setAllowedHeaders);

            convertibleValues
                .get(EXPOSED_HEADERS, ConversionContext.LIST_OF_STRING)
                .ifPresent(configuration::setExposedHeaders);

            convertibleValues
                .get(ALLOW_CREDENTIALS, ConversionContext.BOOLEAN)
                .ifPresent(configuration::setAllowCredentials);

            convertibleValues
                .get(MAX_AGE, ConversionContext.LONG)
                .ifPresent(configuration::setMaxAge);
        }
        return Optional.of(configuration);
    }
}
