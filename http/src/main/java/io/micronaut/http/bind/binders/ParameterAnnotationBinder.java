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
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;

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

    QueryValueArgumentBinder<T> queryValueArgumentBinder;

    /**
     * @param conversionService The conversion service
     */
    public ParameterAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
        this.queryValueArgumentBinder = new QueryValueArgumentBinder<>(conversionService);
    }

    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Argument<T> argument = context.getArgument();
        HttpMethod httpMethod = source.getMethod();
        boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(QueryValue.class);
        String parameterName = argument.getName();

        BindingResult<T> result = queryValueArgumentBinder.bind(context, source);

        Optional<T> val = result.getValue();
        if (!val.isPresent() && !hasAnnotation) {
            // attributes are sometimes added by filters, so this should return unsatisfied if not found
            // so it can be picked up after the filters are executed
            result = doBind(context, source.getAttributes(), parameterName, BindingResult.UNSATISFIED);
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
