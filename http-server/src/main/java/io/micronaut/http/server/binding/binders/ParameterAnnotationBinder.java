/*
 * Copyright 2017 original authors
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
package io.micronaut.http.server.binding.binders;

import io.micronaut.http.annotation.Parameter;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Parameter;
import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * An {@link AnnotatedArgumentBinder} implementation that uses the {@link Parameter}
 * to trigger binding from an HTTP request parameter
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParameterAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Parameter, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<Parameter, T> {
    public ParameterAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Class<Parameter> getAnnotationType() {
        return Parameter.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        Parameter annotation = argument.getAnnotation(Parameter.class);
        boolean hasAnnotation = annotation != null;
        String parameterName = hasAnnotation ? annotation.value() : argument.getName();

        BindingResult<T> result = doBind(context, parameters, parameterName);
        Optional<T> val = result.getValue();
        if(!val.isPresent() && !hasAnnotation) {
            // try attribute
            result = doBind(context, source.getAttributes(), parameterName);
        }
        if(!result.getValue().isPresent() && !hasAnnotation && HttpMethod.requiresRequestBody(source.getMethod())) {
            Optional<ConvertibleValues> body = source.getBody(ConvertibleValues.class);
            if(body.isPresent()) {
                result = doBind(context, body.get(), parameterName);
                if(!result.getValue().isPresent()) {
                    if (ClassUtils.isJavaLangType(argument.getType())) {
                        return Optional::empty;
                    } else {
                        return () -> source.getBody(argument.getType());
                    }
                }
            }
            else {
                //noinspection unchecked
                return BindingResult.UNSATISFIED;
            }
        }
        return result;
    }
}
