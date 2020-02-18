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
package io.micronaut.context.converters;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.reflect.ClassUtils;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A converter registry for converters required by the context.
 *
 * @author graemerocher
 * @since 2.0
 */
@Singleton
@Internal
public class ContextConverterRegistrar implements TypeConverterRegistrar {

    private final BeanContext beanContext;
    private final Map<String, Class> classCache = new ConcurrentHashMap<>(10);

    /**
     * Default constructor.
     * @param beanContext The bean context
     */
    ContextConverterRegistrar(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(String[].class, Class[].class, (object, targetType, context) -> {
            Class[] classes = Arrays
                    .stream(object)
                    .map(str -> conversionService.convert(str, Class.class))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toArray(Class[]::new);

            return Optional.of(classes);
        });

        conversionService.addConverter(String.class, Class.class, (object, targetType, context) -> {
                    final Class result =
                            classCache.computeIfAbsent(object, s -> ClassUtils.forName(s, beanContext.getClassLoader()).orElse(MissingClass.class));
                    if (result == MissingClass.class) {
                        return Optional.empty();
                    }
                    return Optional.of(result);
                }
        );
    }

    /**
     * Used to reference a missing class.
     */
    private final class MissingClass {
    }
}
