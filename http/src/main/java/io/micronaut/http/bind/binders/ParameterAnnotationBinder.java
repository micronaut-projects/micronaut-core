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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
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
 * An {@link io.micronaut.core.bind.annotation.AnnotatedArgumentBinder} implementation that uses the {@link QueryValue}
 * to trigger binding from an HTTP request parameter.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParameterAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<QueryValue, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<QueryValue, T> {

    /**
     * @param conversionService The conversion service
     */
    public ParameterAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        HttpMethod httpMethod = source.getMethod();
        boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(QueryValue.class);
        String parameterName = annotationMetadata.stringValue(QueryValue.class).orElse(argument.getName());
        // If we need to bind all request params to command object
        // checks if the variable is defined with modifier char *
        // eg. ?pojo*
        boolean bindAll = source.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class)
                .flatMap(umi -> umi.getVariables()
                        .stream()
                        .filter(v -> v.getName().equals(parameterName))
                        .findFirst()
                        .map(UriMatchVariable::isExploded)).orElse(false);


        BindingResult<T> result;
        // if the annotation is present or the HTTP method doesn't allow a request body
        // attempt to bind from request parameters. This avoids allowing the request URI to
        // be manipulated to override POST or JSON variables
        if (hasAnnotation || !permitsRequestBody) {
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
        } else {
            //noinspection unchecked
            result = BindingResult.EMPTY;
        }
        Optional<T> val = result.getValue();
        if (!val.isPresent() && !hasAnnotation) {
            // try attribute
            result = doBind(context, source.getAttributes(), parameterName);
        }

        Class argumentType;
        if (argument.getType() == Optional.class) {
            argumentType = argument.getFirstTypeVariable().orElse(argument).getType();
        } else {
            argumentType = argument.getType();
        }

        // If there is still no value at this point and no annotation is specified and
        // the HTTP method allows a request body try and bind from the body
        if (!result.getValue().isPresent() && !hasAnnotation && permitsRequestBody) {
            Optional<ConvertibleValues> body = source.getBody(ConvertibleValues.class);
            if (body.isPresent()) {
                result = doBind(context, body.get(), parameterName);
                if (!result.getValue().isPresent()) {
                    if (ClassUtils.isJavaLangType(argumentType)) {
                        return Optional::empty;
                    } else {
                        //noinspection unchecked
                        return () -> source.getBody(argumentType);
                    }
                }
            } else {
                if (source.getBody().isPresent()) {
                    Optional<String> text = source.getBody(String.class);
                    if (text.isPresent()) {
                        return doConvert(text.get(), context);
                    }
                }
                //noinspection unchecked
                return BindingResult.UNSATISFIED;

            }
        }
        return result;
    }
}
