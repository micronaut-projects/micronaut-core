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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import java.util.List;
import java.util.Map;
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

    @Nullable
    private final String parameterName;
    @Nullable
    private final String fallbackParameterName;
    @Nullable
    private final String defaultValue;
    /**
     * Whether the argument of a specific binder declares no default value, so it does not have to be looked up again.
     */
    private final boolean noDefaultValue;
    /**
     * The conversion of a single string value to the argument of a specific binder.
     */
    @Nullable
    private final TypeConverter<String, T> stringConverter;

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
        this.noDefaultValue = false;
        this.stringConverter = null;
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
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        this.defaultValue = annotationMetadata.stringValue(Bindable.class, DEFAULT_VALUE_MEMBER).orElse(null);
        // Whether the member is declared does not depend on the environment, unlike its resolved value
        this.noDefaultValue = defaultValue == null && !annotationMetadata.isPresent(Bindable.class, DEFAULT_VALUE_MEMBER);
        this.stringConverter = conversionService.resolveConverter(String.class, argument);
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
    protected String getParameterName(Argument<T> argument) {
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

    @Nullable
    private Object resolveValue(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String annotationValue) {
        Argument<T> argument = context.getArgument();
        if (StringUtils.isEmpty(annotationValue)) {
            annotationValue = argument.getName();
            if (annotationValue == null) {
                return null;
            }
        }
        if (stringConverter != null && values instanceof ConvertibleMultiValues<?> multiValues) {
            // a single string value is converted as the multi values convert it
            List<?> all = multiValues.getAll(annotationValue);
            if (all.size() == 1 && all.get(0) instanceof String string) {
                Optional<T> converted = stringConverter.convert(string, argument.getType(), context);
                if (converted.isPresent()) {
                    return converted.get();
                }
                return resolveDefault(argument, context);
            }
        }
        Optional<T> value = values.get(annotationValue, context);
        if (value.isPresent()) {
            return value.get();
        }
        return resolveDefault(argument, context);
    }

    @Nullable
    private Object resolveDefault(Argument<T> argument, ArgumentConversionContext<T> context) {
        String declaredDefault = resolveDefaultValue(argument);
        if (declaredDefault == null) {
            return null;
        }
        if (stringConverter != null) {
            return stringConverter.convert(declaredDefault, argument.getType(), context).orElse(null);
        }
        return conversionService.convert(declaredDefault, context).orElse(null);
    }

    @Nullable
    private String resolveDefaultValue(Argument<T> argument) {
        if (defaultValue == null) {
            if (noDefaultValue) {
                return null;
            }
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
     * Convert value and return a binding result.
     *
     * @param value         The value to convert
     * @param context       The conversion context
     * @param defaultResult The binding result if value is null
     * @return The binding result
     */
    protected BindingResult<T> doConvert(@Nullable Object value, ArgumentConversionContext<T> context, BindingResult<T> defaultResult) {
        if (value == null) {
            Optional<ConversionError> lastError = context.getLastError();
            if (lastError.isPresent()) {
                return new ConvertedBindingResult<>(Optional.empty(), List.of(lastError.get()), false);
            }
            return defaultResult;
        } else {
            Argument<T> argument = context.getArgument();
            boolean optional = argument.getType() == Optional.class;
            Optional<T> result;
            if (!optional && isConverted(argument, value)) {
                result = Optional.of((T) value);
            } else {
                result = conversionService.convert(value, context);
                if (result.isPresent() && optional) {
                    result = (Optional<T>) result.get();
                }
            }
            Optional<ConversionError> lastError = context.getLastError();
            List<ConversionError> conversionErrors = lastError.isPresent() ? List.of(lastError.get()) : List.of();
            // the value was converted with the argument of the context, including its type arguments
            return new ConvertedBindingResult<>(result, conversionErrors, !optional);
        }
    }

    /**
     * Whether the value already is a value of the argument, which the conversion service returns unchanged: an
     * instance of the type of the argument, or of its wrapper type, that is not a collection or a map, since those
     * convert their elements to the type arguments.
     *
     * @param argument The argument
     * @param value    The value
     * @return Whether the value does not need a conversion
     */
    private static boolean isConverted(Argument<?> argument, Object value) {
        return !(value instanceof Iterable) && !(value instanceof Map) && argument.getWrapperType().isInstance(value);
    }

    /**
     * The result of a conversion of {@link #doConvert(Object, ArgumentConversionContext, BindingResult)}.
     * The conversion errors are read from the context once, when the result is created.
     *
     * @param value                     The value
     * @param conversionErrors          The conversion errors
     * @param convertedToArgumentType   Whether the value was converted to the argument of the context
     * @param <T> The argument type
     */
    private record ConvertedBindingResult<T>(Optional<T> value,
                                             List<ConversionError> conversionErrors,
                                             boolean convertedToArgumentType) implements BindingResult<T> {

        @Override
        public Optional<T> getValue() {
            return value;
        }

        @Override
        public List<ConversionError> getConversionErrors() {
            return conversionErrors;
        }

        @Override
        public boolean isSatisfied() {
            return conversionErrors.isEmpty();
        }

        @Override
        public boolean isConvertedToArgumentType() {
            return convertedToArgumentType;
        }
    }
}
