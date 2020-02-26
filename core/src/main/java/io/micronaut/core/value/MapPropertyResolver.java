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
package io.micronaut.core.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;

import java.util.Map;
import java.util.Optional;

/**
 * A {@link PropertyResolver} that resolves values from a backing map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MapPropertyResolver implements PropertyResolver {
    private final Map<String, Object> map;
    private final ConversionService<?> conversionService;

    /**
     * @param map The map to resolves the properties from
     */
    public MapPropertyResolver(Map<String, Object> map) {
        this.map = map;
        this.conversionService = ConversionService.SHARED;
    }

    /**
     * @param map               The map to resolves the properties from
     * @param conversionService The conversion service
     */
    public MapPropertyResolver(Map<String, Object> map, ConversionService conversionService) {
        this.map = map;
        this.conversionService = conversionService;
    }

    @Override
    public boolean containsProperty(String name) {
        return map.containsKey(name);
    }

    @Override
    public boolean containsProperties(String name) {
        return map.keySet().stream().anyMatch(k -> k.startsWith(name));
    }

    @Override
    public <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext) {
        Object value = map.get(name);
        return conversionService.convert(value, conversionContext);
    }
}
