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

package io.micronaut.security.config;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class InterceptUrlMapConverter implements TypeConverter<Map, InterceptUrlMapPattern> {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptUrlMapConverter.class);

    private static final String PATTERN = "pattern";
    private static final String ACCESS = "access";
    private static final String HTTP_METHOD = "httpMethod";

    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    InterceptUrlMapConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * @param m          a Map in the configuration
     * @param targetType The target type being converted to
     * @param context    The {@link ConversionContext}
     * @return An optional InterceptUrlMapConverter
     */
    @Override
    public Optional<InterceptUrlMapPattern> convert(Map m, Class<InterceptUrlMapPattern> targetType, ConversionContext context) {
        if (m == null) {
            return Optional.empty();
        }
        Optional<String> optionalPattern = conversionService.convert(m.get(PATTERN), String.class);
        if (optionalPattern.isPresent()) {
            Optional<List<String>> optionalAccessList = conversionService.convert(m.get(ACCESS), List.class);
            optionalAccessList = optionalAccessList.map((list) -> {
                return list.stream()
                    .map((o) -> conversionService.convert(o, String.class))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
            });
            if (optionalAccessList.isPresent()) {
                Optional<HttpMethod> httpMethod;
                if (m.containsKey(HTTP_METHOD)) {
                    httpMethod = conversionService.convert(m.get(HTTP_METHOD), HttpMethod.class);
                    if (!httpMethod.isPresent()) {
                        throw new ConfigurationException(String.format("interceptUrlMap configuration record %s rejected due to invalid %s key.", m.toString(), HTTP_METHOD));
                    }
                } else {
                    httpMethod = Optional.empty();
                }

                return Optional.of(new InterceptUrlMapPattern(optionalPattern.get(), optionalAccessList.get(), httpMethod.orElse(null)));
            } else {
                throw new ConfigurationException(String.format("interceptUrlMap configuration record %s rejected due to missing or empty %s key.", m.toString(), ACCESS));
            }
        } else {
            throw new ConfigurationException(String.format("interceptUrlMap configuration record %s rejected due to missing %s key.", m.toString(), PATTERN));
        }
    }
}
