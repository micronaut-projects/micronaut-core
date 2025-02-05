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
import io.micronaut.core.bind.annotation.AbstractArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.Collections;

/**
 * Used for binding a parameter exclusively from a path variable.
 *
 * @param <T>
 * @author graemerocher
 * @see PathVariable
 * @since 1.0.3
 */
public class PathVariableAnnotationBinder<T> extends AbstractArgumentBinder<T> implements AnnotatedRequestArgumentBinder<PathVariable, T> {

    /**
     * @param conversionService The conversion service
     */
    public PathVariableAnnotationBinder(ConversionService conversionService) {
        super(conversionService);
    }

    /**
     * @param conversionService The conversion service
     * @param argument The argument
     */
    public PathVariableAnnotationBinder(ConversionService conversionService, Argument<T> argument) {
        super(conversionService, argument);
    }

    @Override
    public Class<PathVariable> getAnnotationType() {
        return PathVariable.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();

        // If we need to bind all request params to command object
        // checks if the variable is defined with modifier char *
        // e.g. ?pojo*
        final UriMatchInfo matchInfo = BasicHttpAttributes.getRouteMatchInfo(source).orElseThrow();
        boolean bindAll = getBindAll(matchInfo, resolvedParameterName(argument));

        final ConvertibleValues<Object> variableValues = ConvertibleValues.of(matchInfo.getVariableValues(), conversionService);
        if (bindAll) {
            Object value;
            // Only maps and POJOs will "bindAll", lists work like normal
            if (Iterable.class.isAssignableFrom(argument.getType())) {
                value = doResolve(context, variableValues);
                if (value == null) {
                    value = Collections.emptyList();
                }
            } else {
                value = parameters.asMap();
            }
            return doConvert(value, context);
        }
        return doBind(context, variableValues, BindingResult.unsatisfied());
    }

    @Override
    protected String getParameterName(Argument<T> argument) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        return annotationMetadata.stringValue(PathVariable.class).orElse(argument.getName());
    }

    private Boolean getBindAll(UriMatchInfo matchInfo, String parameterName) {
        for (UriMatchVariable v : matchInfo.getVariables()) {
            if (v.getName().equals(parameterName)) {
                return v.isExploded();
            }
        }
        return false;
    }

}
