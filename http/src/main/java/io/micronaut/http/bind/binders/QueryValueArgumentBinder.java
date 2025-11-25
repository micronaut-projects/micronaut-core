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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.bind.annotation.AbstractArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A binder for binding arguments annotated with @QueryValue.
 *
 * @param <T> The argument type
 * @author James Kleeh
 * @author Andriy Dmytruk
 * @since 2.0.2
 */
public class QueryValueArgumentBinder<T> extends AbstractArgumentBinder<T> implements AnnotatedRequestArgumentBinder<QueryValue, T> {

    /**
     * Constructor.
     *
     * @param conversionService conversion service
     */
    public QueryValueArgumentBinder(ConversionService conversionService) {
        super(conversionService);
    }

    /**
     * Constructor.
     *
     * @param conversionService conversion service
     * @param argument          The argument
     */
    public QueryValueArgumentBinder(ConversionService conversionService, Argument<T> argument) {
        super(conversionService, argument);
    }

    @Override
    public RequestArgumentBinder<T> createSpecific(Argument<T> argument) {
        return new QueryValueArgumentBinder<>(conversionService, argument);
    }

    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    /**
     * Binds the argument with {@link QueryValue} annotation to the request
     * (Also binds without annotation if request body is not permitted).
     * <p>
     * It will first try to convert to ConvertibleMultiValues type and if conversion is successful, add the
     * corresponding parameters to the request. (By default the conversion will be successful if the {@link Format}
     * annotation is present and has one of the supported values - see
     * {@link io.micronaut.core.convert.converters.MultiValuesConverterFactory} for specific converters). Otherwise,
     * the uri template will be used to deduce what will be done with the request. For example, simple parameters are
     * converted to {@link String}
     */
    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();

        if (source.getMethod().permitsRequestBody() && !annotationMetadata.hasAnnotation(QueryValue.class)) {
            // During the unmatched check avoid requests that don't allow bodies
            return BindingResult.unsatisfied();
        }

        BindingResult<T> bindSimpleResult = bindSimple(context, source, annotationMetadata, parameters, argument);
        if (bindSimpleResult.isSatisfied()) {
            return bindSimpleResult;
        }
        return bindPojo(context, parameters, argument);
    }

    private BindingResult<T> bindSimple(ArgumentConversionContext<T> context,
                                        HttpRequest<?> source,
                                        AnnotationMetadata annotationMetadata,
                                        ConvertibleMultiValues<String> parameters,
                                        Argument<T> argument) {
        // First try converting from the ConvertibleMultiValues type and if conversion is successful, return it.
        // Otherwise, use the given uri template to deduce what to do with the variable
        Optional<T> multiValueConversion;
        if (annotationMetadata.hasAnnotation(Format.class)) {
            multiValueConversion = conversionService.convert(parameters, context);
        } else {
            multiValueConversion = Optional.empty();
        }

        if (multiValueConversion.isPresent()) {
            return () -> multiValueConversion;
        }


        // If we need to bind all request params to command object
        // checks if the variable is defined with modifier char *, e.g. ?pojo*
        String parameterName = resolvedParameterName(argument);
        boolean bindAll = BasicHttpAttributes.getRouteMatchInfo(source)
            .map(umi -> {
                UriMatchVariable uriMatchVariable = umi.getVariableMap().get(parameterName);
                return uriMatchVariable != null && uriMatchVariable.isExploded();
            }).orElse(false);

        if (bindAll) {
            Object value;
            // Only maps and POJOs will "bindAll", lists work like normal
            if (Iterable.class.isAssignableFrom(argument.getType())) {
                value = doResolve(context, parameters);
                if (value == null) {
                    value = Collections.emptyList();
                }
            } else {
                value = parameters.asMap();
            }
            return doConvert(value, context);
        }
        return doBind(context, parameters, BindingResult.unsatisfied());
    }

    private BindingResult<T> bindPojo(ArgumentConversionContext<T> context,
                                      ConvertibleMultiValues<String> parameters,
                                      Argument<T> argument) {
        Optional<BeanIntrospection<T>> introspectionOpt = BeanIntrospector.SHARED.findIntrospection(argument.getType());
        if (introspectionOpt.isEmpty()) {
            return BindingResult.unsatisfied();
        }

        BeanIntrospection<T> introspection = introspectionOpt.get();
        BeanIntrospection.Builder<T> introspectionBuilder = introspection.builder();
        Argument<?>[] builderArguments = introspectionBuilder.getBuilderArguments();

        for (int index = 0; index < builderArguments.length; index++) {
            Argument<?> builderArg = builderArguments[index];
            String propertyName = builderArg.getName();
            List<String> values = parameters.getAll(propertyName);
            boolean hasNoValue = values.isEmpty();
            @Nullable String defaultValue = hasNoValue ? builderArg
                .getAnnotationMetadata()
                .stringValue(Bindable.class, "defaultValue").orElse(null) : null;

            ArgumentConversionContext<?> conversionContext = context.with(builderArg);
            Optional<?> converted = hasNoValue ? conversionService.convert(defaultValue, conversionContext) : conversionService.convert(values, conversionContext);
            if (converted.isPresent()) {
                try {
                    @SuppressWarnings({"unchecked"})
                    Argument<Object> rawArg = (Argument<Object>) builderArg;
                    introspectionBuilder.with(index, rawArg, converted.get());
                } catch (Exception e) {
                    context.reject(builderArg, e);
                    return BindingResult.unsatisfied();
                }
            } else if (conversionContext.hasErrors()) {
                ConversionError conversionError = conversionContext.getLastError().orElse(null);
                if (conversionError != null) {
                    Exception cause = conversionError.getCause();
                    context.reject(builderArg, cause);
                    return BindingResult.unsatisfied();
                }
            }
        }

        try {
            T instance = introspectionBuilder.build();
            return () -> Optional.of(instance);
        } catch (Exception e) {
            context.reject(argument, e);
            return BindingResult.unsatisfied();
        }
    }

    @Override
    protected String getParameterName(Argument<T> argument) {
        return argument.getAnnotationMetadata().stringValue(QueryValue.class).orElse(argument.getName());
    }
}
