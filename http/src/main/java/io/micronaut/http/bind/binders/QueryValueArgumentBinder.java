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
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.Collections;
import java.util.Optional;

/**
 * A binder for binding arguments annotated with @QueryValue.
 *
 * @param <T> The argument type
 * @author James Kleeh
 * @author Andriy Dmytruk
 * @since 2.0.2
 */
public class QueryValueArgumentBinder<T>
        extends AbstractAnnotatedArgumentBinder<QueryValue, T, HttpRequest<?>>
        implements AnnotatedRequestArgumentBinder<QueryValue, T> {

    private final ConversionService<?> conversionService;

    /**
     * Constructor.
     *
     * @param conversionService conversion service
     */
    public QueryValueArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService);
        this.conversionService = conversionService;
    }

    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    /**
     * Binds the argument with {@link QueryValue} annotation to the request
     * (Also binds without annotation if request body is not permitted).
     *
     * It will first try to convert to ConvertibleMultiValues type and if conversion is successful, add the
     * corresponding parameters to the request. (By default the conversion will be successful if the {@link Format}
     * annotation is present and has one of the supported values - see
     * {@link io.micronaut.core.convert.converters.MultiValuesConverterFactory} for specific converters). Otherwise
     * the uri template will be used to deduce what will be done with the request. For example, simple parameters are
     * converted to {@link String}
     */
    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(QueryValue.class);

        HttpMethod httpMethod = source.getMethod();
        boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

        BindingResult<T> result;
        if (hasAnnotation || !permitsRequestBody) {
            // First try converting from the ConvertibleMultiValues type and if conversion is successful, return it.
            // Otherwise use the given uri template to deduce what to do with the variable
            Optional<T> multiValueConversion;
            if (annotationMetadata.hasAnnotation(Format.class)) {
                multiValueConversion = conversionService.convert(parameters, context);
            } else {
                multiValueConversion = Optional.empty();
            }

            if (multiValueConversion.isPresent()) {
                result = () -> multiValueConversion;
            } else {
                String parameterName = annotationMetadata.stringValue(QueryValue.class).orElse(argument.getName());

                // If we need to bind all request params to command object
                // checks if the variable is defined with modifier char *, eg. ?pojo*
                boolean bindAll = source.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class)
                        .map(umi -> {
                            UriMatchVariable uriMatchVariable = umi.getVariableMap().get(parameterName);
                            return uriMatchVariable != null && uriMatchVariable.isExploded();
                        }).orElse(false);

                if (bindAll) {
                    Object value;
                    // Only maps and POJOs will "bindAll", lists work like normal
                    if (Iterable.class.isAssignableFrom(argument.getType())) {
                        value = doResolve(context, parameters, parameterName);
                        if (value == null) {
                            value = Collections.emptyList();
                        }
                    } else {
                        value = parameters.asMap();
                    }
                    result = doConvert(value, context);
                } else {
                    result = doBind(context, parameters, parameterName);
                }
            }
        } else {
            result = BindingResult.EMPTY;
        }
        return result;
    }
}
