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

import io.micronaut.core.bind.annotation.AbstractArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;

import java.util.Optional;

/**
 * Binds a String body argument.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBodyAnnotationBinder<T> extends AbstractArgumentBinder<T> implements BodyArgumentBinder<T>, PostponedRequestArgumentBinder<T> {

    protected final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        super(conversionService);
        this.conversionService = conversionService;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (!source.getMethod().permitsRequestBody()) {
            return BindingResult.unsatisfied();
        }

        Optional<?> body = source.getBody();
        if (body.isEmpty()) {
            return BindingResult.empty();
        }
        boolean annotatedAsBody = context.getAnnotationMetadata().hasAnnotation(Body.class);
        Optional<String> optionalBodyComponent = context.getAnnotationMetadata().stringValue(Body.class);
        String bodyComponent = optionalBodyComponent.orElseGet(() -> {
            if (annotatedAsBody) {
                return null;
            }
            return context.getArgument().getName();
        });
        if (bodyComponent != null) {
            Optional<ConvertibleValues> convertibleValuesBody = source.getBody(ConvertibleValues.class);
            if (convertibleValuesBody.isPresent()) {
                BindingResult<T> convertibleValuesBindingResult = doBind(context, convertibleValuesBody.get(), bodyComponent);
                if (convertibleValuesBindingResult.getValue().isPresent() || !convertibleValuesBindingResult.getConversionErrors().isEmpty()) {
                    return convertibleValuesBindingResult;
                }
            }
        }
        BindingResult<T> bindingResult = doConvert(body.get(), context);
        if (!annotatedAsBody && bindingResult.getValue().isEmpty()) {
            return BindingResult.empty();
        }
        return bindingResult;
    }

}
