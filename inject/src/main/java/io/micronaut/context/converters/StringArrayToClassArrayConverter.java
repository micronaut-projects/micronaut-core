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

package io.micronaut.context.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.reflect.ClassUtils;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Optional;

/**
 * Converts a String[] to a Class[].
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class StringArrayToClassArrayConverter implements TypeConverter<Object[], Class[]> {

    private final ClassLoader classLoader;

    /**
     * @param classLoader The class loader
     */
    public StringArrayToClassArrayConverter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Optional<Class[]> convert(Object[] object, Class<Class[]> targetType, ConversionContext context) {
        Class[] classes = Arrays
            .stream(object)
            .map(str -> ClassUtils.forName(str.toString(), classLoader))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toArray(Class[]::new);

        return Optional.of(classes);
    }
}
