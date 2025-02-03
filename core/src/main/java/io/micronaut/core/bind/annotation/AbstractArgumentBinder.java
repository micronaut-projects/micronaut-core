/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.bind.annotation;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * An abstract {@link AnnotatedArgumentBinder} implementation.
 *
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractArgumentBinder<T> {

    private static final String DEFAULT_VALUE_MEMBER = "defaultValue";
    protected final ConversionService conversionService;

    private final String parameterName;
    private final String fallbackParameterName;
    private final String defaultValue;

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    protected AbstractArgumentBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.parameterName = null;
        this.fallbackParameterName = null;
        this.defaultValue = null;
    }

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     * @param argument The argument
     */
    protected AbstractArgumentBinder(ConversionService conversionService, Argument<T> argument) {
        this.conversionService = conversionService;
        this.parameterName = getParameterName(argument);
        this.fallbackParameterName = getFallbackFormat(argument);
        this.defaultValue = argument.getAnnotationMetadata().stringValue(Bindable.class, DEFAULT_VALUE_MEMBER).orElse(null);
    }

    /**
     * Do binding.
     *
     * @param context context
     * @param values  values
     * @param name    name
     * @return result
     */
    protected BindingResult<T> doBind(
        ArgumentConversionContext<T> context,
        ConvertibleValues<?> values,
        String name) {
        return doBind(context, values, name, BindingResult.empty());
    }

    /**
     * Do binding.
     *
     * @param context context
     * @param values  values
     * @return result
     */
    protected BindingResult<T> doBind(
        ArgumentConversionContext<T> context,
        ConvertibleValues<?> values) {
        return doBind(context, values, resolvedParameterName(context.getArgument()), BindingResult.empty());
    }

    /**
     * Do binding.
     *
     * @param context       context
     * @param values        values
     * @param name          annotationValue
     * @param defaultResult The default binding result if the value is null
     * @return result
     */
    protected BindingResult<T> doBind(ArgumentConversionContext<T> context,
                                      ConvertibleValues<?> values,
                                      String name,
                                      BindingResult<T> defaultResult) {

        return doConvert(doResolve(context, values, name), context, defaultResult);
    }

    /**
     * Do binding.
     *
     * @param context       context
     * @param values        values
     * @param defaultResult The default binding result if the value is null
     * @return result
     * @since 4.8
     */
    protected BindingResult<T> doBind(ArgumentConversionContext<T> context,
                                      ConvertibleValues<?> values,
                                      BindingResult<T> defaultResult) {

        return doBind(context, values, resolvedParameterName(context.getArgument()), defaultResult);
    }

    /**
     * Find the parameter name.
     *
     * @param argument The argument
     * @return The name
     * @since 4.8
     */
    @NonNull
    protected String getParameterName(@NonNull Argument<T> argument) {
        throw new IllegalStateException("Parameter resolved method must be implemented!");
    }

    /**
     * Returns resolved parameter name using {@link #getParameterName(Argument)} or pre-resolved.
     * @param argument The argument.
     * @return The parameter name
     * @since 4.8
     */
    protected final String resolvedParameterName(Argument<T> argument) {
        if (parameterName == null) {
            return getParameterName(argument);
        }
        return parameterName;
    }

    /**
     * Do resolve.
     *
     * @param context context
     * @param values  values
     * @param name    annotationValue
     * @return result
     */
    @Nullable
    protected Object doResolve(ArgumentConversionContext<T> context,
                               ConvertibleValues<?> values,
                               String name) {

        Object value = resolveValue(context, values, name);
        if (value == null) {
            String fallbackName = getFallbackFormatInternal(context.getArgument());
            if (!name.equals(fallbackName)) {
                name = fallbackName;
                value = resolveValue(context, values, name);
            }
        }

        return value;
    }

    /**
     * Do resolve.
     *
     * @param context context
     * @param values  values
     * @return result
     * @since 4.8
     */
    @Nullable
    protected Object doResolve(ArgumentConversionContext<T> context,
                               ConvertibleValues<?> values) {
        String name = resolvedParameterName(context.getArgument());
        return doResolve(context, values, name);
    }

    /**
     * @param argument The argument
     * @return The fallback format
     */
    private String getFallbackFormatInternal(Argument<?> argument) {
        if (fallbackParameterName == null) {
            return NameUtils.hyphenate(argument.getName());
        }
        return fallbackParameterName;
    }

    /**
     * @param argument The argument
     * @return The fallback format
     */
    protected String getFallbackFormat(Argument<?> argument) {
        return NameUtils.hyphenate(argument.getName());
    }

    private Object resolveValue(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String annotationValue) {
        Argument<T> argument = context.getArgument();
        if (StringUtils.isEmpty(annotationValue)) {
            annotationValue = argument.getName();
        }
        return values.get(annotationValue, context).orElseGet(() ->
            conversionService.convert(
                resolveDefaultValue(argument),
                context
            ).orElse(null)
        );
    }

    private String resolveDefaultValue(Argument<T> argument) {
        if (defaultValue == null) {
            return argument.getAnnotationMetadata().stringValue(Bindable.class, DEFAULT_VALUE_MEMBER).orElse(null);
        }
        return defaultValue;
    }

    /**
     * Convert the value and return a binding result.
     *
     * @param value   The value to convert
     * @param context The conversion context
     * @return The binding result
     */
    protected BindingResult<T> doConvert(Object value, ArgumentConversionContext<T> context) {
        return doConvert(value, context, BindingResult.empty());
    }

    /**
     * Convert the value and return a binding result.
     *
     * @param value         The value to convert
     * @param context       The conversion context
     * @param defaultResult The binding result if the value is null
     * @return The binding result
     */
    protected BindingResult<T> doConvert(Object value, ArgumentConversionContext<T> context, BindingResult<T> defaultResult) {
        if (value == null) {
            Optional<ConversionError> lastError = context.getLastError();
            if (lastError.isPresent()) {
                return new BindingResult<>() {
                    @Override
                    public Optional<T> getValue() {
                        return Optional.empty();
                    }

                    @Override
                    public List<ConversionError> getConversionErrors() {
                        return lastError.map(List::of).orElseGet(List::of);
                    }
                };
            }
            return defaultResult;
        } else {
            Optional<T> result = conversionService.convert(value, context);
            Optional<ConversionError> lastError = context.getLastError();
            if (result.isPresent() && context.getArgument().getType() == Optional.class) {
                result = (Optional<T>) result.get();
            }
            Optional<T> finalResult = result;
            return new BindingResult<>() {
                @Override
                public Optional<T> getValue() {
                    return finalResult;
                }

                @Override
                public List<ConversionError> getConversionErrors() {
                    return lastError.map(List::of).orElseGet(List::of);
                }
            };
        }
    }
}
