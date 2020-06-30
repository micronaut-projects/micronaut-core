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
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.Collections;
import java.util.Optional;

/**
 * Used for binding a parameter exclusively from a path variable.
 *
 * @author graemerocher
 * @since 1.0.3
 * @see PathVariable
 * @param <T>
 */
public class PathVariableAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<PathVariable, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<PathVariable, T> {

    /**
     * @param conversionService The conversion service
     */
    public PathVariableAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Class<PathVariable> getAnnotationType() {
        return PathVariable.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        HttpMethod httpMethod = source.getMethod();

        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(PathVariable.class);
        String parameterName = annotationMetadata.stringValue(PathVariable.class).orElse(argument.getName());
        // If we need to bind all request params to command object
        // checks if the variable is defined with modifier char *
        // eg. ?pojo*
        final Optional<UriMatchInfo> matchInfo = source.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class);
        boolean bindAll = matchInfo
                .flatMap(umi -> umi.getVariables()
                        .stream()
                        .filter(v -> v.getName().equals(parameterName))
                        .findFirst()
                        .map(UriMatchVariable::isExploded)).orElse(false);


        BindingResult<T> result;
        // if the annotation is present or the HTTP method doesn't allow a request body
        // attempt to bind from request parameters. This avoids allowing the request URI to
        // be manipulated to override POST or JSON variables
        if (hasAnnotation && matchInfo.isPresent()) {
            final ConvertibleValues<Object> variableValues = ConvertibleValues.of(matchInfo.get().getVariableValues());
            if (bindAll) {
                Object value;
                // Only maps and POJOs will "bindAll", lists work like normal
                if (Iterable.class.isAssignableFrom(argument.getType())) {
                    value = doResolve(context, variableValues, parameterName);
                    if (value == null) {
                        value = Collections.emptyList();
                    }
                } else {
                    value = parameters.asMap();
                }
                result = doConvert(value, context);
            } else {
                result = doBind(context, variableValues, parameterName);
            }
        } else {
            //noinspection unchecked
            result = BindingResult.EMPTY;
        }

        return result;
    }
}
