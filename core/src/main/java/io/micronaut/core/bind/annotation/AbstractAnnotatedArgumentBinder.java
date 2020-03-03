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
package io.micronaut.core.bind.annotation;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * An abstract {@link AnnotatedArgumentBinder} implementation.
 *
 * @param <A> The annotation type
 * @param <T> The argument type
 * @param <S> The binding source type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotatedArgumentBinder<A extends Annotation, T, S> implements AnnotatedArgumentBinder<A, T, S> {

    private static final String DEFAULT_VALUE_MEMBER = "defaultValue";
    private final ConversionService<?> conversionService;

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    protected AbstractAnnotatedArgumentBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Do binding.
     *
     * @param context         context
     * @param values          values
     * @param annotationValue annotationValue
     * @return result
     */
    @SuppressWarnings("unchecked")
    protected BindingResult<T> doBind(
        ArgumentConversionContext<T> context,
        ConvertibleValues<?> values,
        String annotationValue) {
        return doBind(context, values, annotationValue, BindingResult.EMPTY);
    }

    /**
     * Do binding.
     *
     * @param context         context
     * @param values          values
     * @param annotationValue annotationValue
     * @param defaultResult   The default binding result if the value is null
     * @return result
     */
    @SuppressWarnings("unchecked")
    protected BindingResult<T> doBind(
            ArgumentConversionContext<T> context,
            ConvertibleValues<?> values,
            String annotationValue,
            ArgumentBinder.BindingResult<T> defaultResult) {

        return doConvert(doResolve(context, values, annotationValue), context, defaultResult);
    }

    /**
     * Do resolve.
     *
     * @param context         context
     * @param values          values
     * @param annotationValue annotationValue
     * @return result
     */
    @SuppressWarnings("unchecked")
    protected @Nullable Object doResolve(
            ArgumentConversionContext<T> context,
            ConvertibleValues<?> values,
            String annotationValue) {

        Object value = resolveValue(context, values, annotationValue);
        if (value == null) {
            String fallbackName = getFallbackFormat(context.getArgument());
            if (!annotationValue.equals(fallbackName)) {
                annotationValue = fallbackName;
                value = resolveValue(context, values, annotationValue);
            }
        }

        return value;
    }

    /**
     * @param argument The argument
     * @return The fallback format
     */
    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(argument.getName());
    }

    private Object resolveValue(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String annotationValue) {
        Argument<T> argument = context.getArgument();
        if (StringUtils.isEmpty(annotationValue)) {
            annotationValue = argument.getName();
        }
        return values.get(annotationValue, context).orElseGet(() ->
                conversionService.convert(argument.getAnnotationMetadata().stringValue(Bindable.class, DEFAULT_VALUE_MEMBER).orElse(null), context).orElse(null)
        );
    }

    /**
     * Convert the value and return a binding result.
     *
     * @param value The value to convert
     * @param context The conversion context
     * @return The binding result
     */
    protected BindingResult<T> doConvert(Object value, ArgumentConversionContext<T> context) {
        return doConvert(value, context, BindingResult.EMPTY);
    }

    /**
     * Convert the value and return a binding result.
     *
     * @param value The value to convert
     * @param context The conversion context
     * @param defaultResult The binding result if the value is null
     * @return The binding result
     */
    protected BindingResult<T> doConvert(Object value, ArgumentConversionContext<T> context, ArgumentBinder.BindingResult<T> defaultResult) {
        if (value == null) {
            return defaultResult;
        } else {
            Optional<T> result = conversionService.convert(value, context);
            if (result.isPresent() && context.getArgument().getType() == Optional.class) {
                return () -> (Optional<T>) result.get();
            }
            return () -> result;
        }
    }
}
