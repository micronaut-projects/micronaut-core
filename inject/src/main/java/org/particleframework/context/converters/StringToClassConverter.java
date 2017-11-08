/*
 * Copyright 2017 original authors
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
package org.particleframework.context.converters;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.reflect.ClassUtils;

import java.util.Optional;

/**
 * Converts a String to a class
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StringToClassConverter implements TypeConverter<CharSequence,Class> {
    private final ClassLoader classLoader;

    public StringToClassConverter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Optional<Class> convert(CharSequence object, Class<Class> targetType, ConversionContext context) {
        return ClassUtils.forName(object.toString(), classLoader);
    }
}
