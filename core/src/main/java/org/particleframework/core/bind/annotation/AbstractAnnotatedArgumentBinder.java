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
import org.particleframework.core.util.StringUtils;

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
public abstract class AbstractAnnotatedArgumentBinder<A extends Annotation, T, S> implements AnnotatedArgumentBinder<A, T, S> {

    private final ConversionService<?> conversionService;

    protected AbstractAnnotatedArgumentBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @SuppressWarnings("unchecked")
    protected BindingResult<T> doBind(
            ArgumentConversionContext<T> context,
            ConvertibleValues<?> values,
            String annotationValue) {
        Object value = resolveValue(context, values, annotationValue);
        if (value == null) {
            String fallbackName = getFallbackFormat(context.getArgument());
            if (!annotationValue.equals(fallbackName)) {

                annotationValue = fallbackName;
                value = resolveValue(context, values, annotationValue);
                if (value == null) {
                    return BindingResult.EMPTY;
                }
            }
        }

        return doConvert(value, context);
    }

    private Object resolveValue(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String annotationValue) {
        Argument<T> argument = context.getArgument();
        if (StringUtils.isEmpty(annotationValue)) {
            annotationValue = argument.getName();
        }
        return values.get(annotationValue, context).orElse(null);
    }

    private BindingResult<T> doConvert(Object value, ArgumentConversionContext<T> context) {
        Optional<T> result = conversionService.convert(value, context);
        if (result.isPresent() && context.getArgument().getType() == Optional.class) {
            return () -> (Optional<T>) result.get();
        } else {
            return () -> result;
        }
    }

    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(argument.getName());
    }
}

