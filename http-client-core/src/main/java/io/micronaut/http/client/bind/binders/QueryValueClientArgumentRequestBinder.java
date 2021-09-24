/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.client.bind.binders;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the Binder for {@link QueryValue}
 * The details of implementation can be found in the
 * {@link #bind(ArgumentConversionContext, ClientRequestUriContext, Object, MutableHttpRequest)} bind()} method javadoc.
 *
 * @author Andriy Dmytruk
 * @since 3.0.0
 */
public class QueryValueClientArgumentRequestBinder implements AnnotatedClientArgumentRequestBinder<QueryValue> {

    private final ConversionService<?> conversionService;

    public QueryValueClientArgumentRequestBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    @NonNull
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    /**
     * If value can be converted to ConvertibleMultiValues, then use it and add it to the uriContext.queryParameters.
     * The ConvertibleMultiValues converters are found in
     * {@link io.micronaut.core.convert.converters.MultiValuesConverterFactory} and perform conversion only when the
     * {@link Format} annotation has one of the supported values.
     * Otherwise if the {@link Format} annotation is present, it is converted to {@link String}. If none of these
     * are satisfied, the{@link io.micronaut.http.uri.UriTemplate} decides what to do with the given value which
     * is supplied as an Object (it is added to uriContext.pathParameter).
     *
     * <br> By default value is converted to ConvertibleMultiValues when the {@link Format} annotation is present and has
     * one of the defined above formats. Otherwise empty optional is returned.
     *
     * <br> The default {@link io.micronaut.http.uri.UriTemplate} will convert the value to String and to parameters.
     * Optionally, the value can be formatted if the path template states so.
     */
    @Override
    public void bind(
            @NonNull ArgumentConversionContext<Object> context,
            @NonNull ClientRequestUriContext uriContext,
            @NonNull Object value,
            @NonNull MutableHttpRequest<?> request
    ) {
        String parameterName = context.getAnnotationMetadata().stringValue(QueryValue.class)
                .filter(StringUtils::isNotEmpty)
                .orElse(context.getArgument().getName());

        final UriMatchVariable uriVariable = uriContext.getUriTemplate().getVariables()
                .stream()
                .filter(v -> v.getName().equals(parameterName))
                .findFirst()
                .orElse(null);

        if (uriVariable != null) {
            if (uriVariable.isExploded()) {
                uriContext.setPathParameter(parameterName, value);
            } else {
                String convertedValue
                        = conversionService.convert(value, ConversionContext.STRING.with(context.getAnnotationMetadata()))
                        .filter(StringUtils::isNotEmpty)
                        .orElse(null);
                if (convertedValue != null) {
                    uriContext.setPathParameter(parameterName, convertedValue);
                } else {
                    uriContext.setPathParameter(parameterName, value);
                }
            }
        } else {
            ArgumentConversionContext<ConvertibleMultiValues> conversionContext = context.with(
                    Argument.of(ConvertibleMultiValues.class, context.getArgument().getName(), context.getAnnotationMetadata()));
            final Optional<ConvertibleMultiValues<String>> multiValues = conversionService.convert(value, conversionContext)
                    .map(values -> (ConvertibleMultiValues<String>) values);
            if (multiValues.isPresent())  {
                Map<String, List<String>> queryParameters = uriContext.getQueryParameters();
                // Add all the parameters
                multiValues.get().forEach((k, v) -> {
                    if (queryParameters.containsKey(k)) {
                        queryParameters.get(k).addAll(v);
                    } else {
                        queryParameters.put(k, v);
                    }
                });
            } else {
                conversionService.convert(value, ConversionContext.STRING.with(context.getAnnotationMetadata()))
                        .ifPresent(v -> uriContext.addQueryParameter(parameterName, v));
            }
        }
    }
}
