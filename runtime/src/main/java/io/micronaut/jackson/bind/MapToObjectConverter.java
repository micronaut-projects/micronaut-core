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

import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A class that uses the {@link BeanPropertyBinder} to bind maps to {@link Object} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class MapToObjectConverter implements TypeConverter<Map, Object> {

    private final Provider<BeanPropertyBinder> beanPropertyBinder;

    /**
     * @param beanPropertyBinder To bind map and Java bean properties
     */
    @Inject
    public MapToObjectConverter(Provider<BeanPropertyBinder> beanPropertyBinder) {
        this.beanPropertyBinder = beanPropertyBinder;
    }

    @Override
    public Optional<Object> convert(Map map, Class<Object> targetType, ConversionContext context) {
        final BiFunction<Object, Map<?, ?>, Object> propertiesBinderFunction = (object, properties) -> {
            Map bindMap = new LinkedHashMap(properties.size());
            for (Map.Entry entry : properties.entrySet()) {
                Object key = entry.getKey();
                bindMap.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), entry.getValue());
            }
            return beanPropertyBinder.get().bind(object, bindMap);
        };

        Optional<Object> instance = InstantiationUtils.tryInstantiate(targetType, map, context)
                    .map(object -> propertiesBinderFunction.apply(object, map));

        if (instance.isPresent()) {
            return instance;
        } else if (targetType.isInstance(map)) {
            return Optional.of(map);
        } else {
            return InstantiationUtils
                    .tryInstantiate(targetType)
                    .map(object -> propertiesBinderFunction.apply(object, map));
        }
    }
}
