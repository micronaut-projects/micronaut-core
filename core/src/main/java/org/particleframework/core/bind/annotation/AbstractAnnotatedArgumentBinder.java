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
package org.particleframework.core.bind.annotation;

import org.particleframework.core.convert.*;
import org.particleframework.core.convert.value.ConvertibleMultiValues;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.type.Argument;

import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Stream;

/**
 * An abstract {@link AnnotatedArgumentBinder} implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotatedArgumentBinder <A extends Annotation, T, S> implements AnnotatedArgumentBinder<A, T, S> {

    private final ConversionService<?> conversionService;

    protected AbstractAnnotatedArgumentBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    protected Optional<T> doBind(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String annotationValue, Locale locale, Charset characterEncoding) {
        Argument<T> argument = context.getArgument();
        Class<T> argumentType = argument.getType();
        Object value = resolveValue(argument, values, argumentType, annotationValue);
        if(value == null) {
            String fallbackName = getFallbackFormat(argument);
            if(!annotationValue.equals(fallbackName)) {

                annotationValue = fallbackName;
                value = resolveValue(argument, values, argumentType, annotationValue);
                if(value == null) {
                    return Optional.empty();
                }
            }
        }

        return doConvert(value, argumentType, context);
    }

    private Object resolveValue(Argument<T> argument, ConvertibleValues<?> values, Class<T> argumentType, String annotationValue) {
        if(annotationValue.length() == 0) {
            annotationValue = argument.getName();
        }
        Object value = values.get(annotationValue, Object.class).orElse(null);
        boolean isConvertibleValues = values instanceof ConvertibleMultiValues;
        if(isConvertibleValues && isManyObjects(argument)) {
            ConvertibleMultiValues<?> multiValues = (ConvertibleMultiValues<?>) values;
            List<?> all = multiValues.getAll(annotationValue);
            boolean hasMultiValues = all != null;
            if(hasMultiValues && all.isEmpty()) {
                return null;
            }
            if(hasMultiValues && all.size()>1) {
                value = all;
            }

        }
        else if(Map.class.isAssignableFrom(argumentType)) {
            if(isConvertibleValues) {
                ConvertibleMultiValues<?> multiValues = (ConvertibleMultiValues<?>) values;
                Map<String, Argument<?>> typeParameters = argument.getTypeVariables();

                Class valueType;
                if(typeParameters.containsKey("V")) {
                    valueType = typeParameters.get("V").getType();
                }
                else {
                    valueType = Object.class;
                }

                value = multiValues.subMap(annotationValue, valueType);
            }
            else if(Arrays.asList("parameters", "params").contains(annotationValue)) {
                value = values;
            }
        }
        return value;
    }

    private boolean isManyObjects(Argument<?> argument) {
        Class<?> argumentType = argument.getType();
        if(argumentType.isArray() || Iterable.class.isAssignableFrom(argumentType) || Stream.class.isAssignableFrom(argumentType)) {
            return true;
        }
        else {
            Optional<Argument<?>> firstTypeVariable = argument.getFirstTypeVariable();
            if(firstTypeVariable.isPresent()) {
                Argument<?> typeVariable = firstTypeVariable.get();
                return isManyObjects(typeVariable);
            }
        }
        return false;
    }

    private Optional<T> doConvert(Object value, Class<T> targetType, ConversionContext context) {
        Optional<T> result = conversionService.convert(value, targetType, context);
        if(targetType == Optional.class && result.isPresent() ) {
            return (Optional<T>)result.get();
        }
        else {
            return result;
        }
    }

    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(argument.getName());
    }
}

