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
package io.micronaut.jackson.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.naming.NameUtils;
import javax.inject.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A class that uses the {@link BeanPropertyBinder} to bind maps to {@link Object} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link io.micronaut.jackson.convert.JacksonConverterRegistrar}
 */
@Deprecated
public class MapToObjectConverter implements TypeConverter<Map, Object> {

    private final Provider<BeanPropertyBinder> beanPropertyBinder;

    /**
     * @param beanPropertyBinder To bind map and Java bean properties
     */
    public MapToObjectConverter(Provider<BeanPropertyBinder> beanPropertyBinder) {
        this.beanPropertyBinder = beanPropertyBinder;
    }

    @Override
    public Optional<Object> convert(Map map, Class<Object> targetType, ConversionContext context) {
        ArgumentConversionContext<Object> conversionContext;
        if (context instanceof ArgumentConversionContext) {
            conversionContext = (ArgumentConversionContext<Object>) context;
        } else {
            conversionContext = ConversionContext.of(targetType);
        }
        Map mapWithExtraProps = new LinkedHashMap(map.size());
        for (Map.Entry entry : ((Map<?, ?>) map).entrySet()) {
            Object key = entry.getKey();
            mapWithExtraProps.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), entry.getValue());
        }
        ArgumentBinder binder = this.beanPropertyBinder.get();
        ArgumentBinder.BindingResult result = binder.bind(conversionContext, mapWithExtraProps);
        Optional opt = result.getValue();
        return opt;
    }
}
