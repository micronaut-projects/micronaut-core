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
package org.particleframework.http.binding.binders.request;

import org.particleframework.bind.annotation.AbstractAnnotatedArgumentBinder;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.ConvertibleMultiValues;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.binding.annotation.Parameter;
import org.particleframework.inject.Argument;

import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParameterAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Parameter, T, HttpRequest> {
    public ParameterAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Class<Parameter> annotationType() {
        return Parameter.class;
    }

    @Override
    public Optional<T> bind(Argument<T> argument, HttpRequest source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Parameter annotation = argument.getAnnotation(Parameter.class);
        String parameterName = annotation == null ? argument.getName() : annotation.value();
        return doBind(argument, parameters, parameterName);
    }
}
