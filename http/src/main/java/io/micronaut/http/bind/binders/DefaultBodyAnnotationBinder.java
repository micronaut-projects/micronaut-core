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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Binds a String body argument.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBodyAnnotationBinder<T> implements BodyArgumentBinder<T> {

    protected final ConversionService<?> conversionService;

    /**
     * @param conversionService The conversion service
     */
    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Optional<String> bodyComponent = context.getAnnotationMetadata().stringValue(Body.class);
        if (bodyComponent.isPresent()) {
            Optional<ConvertibleValues> body = source.getBody(ConvertibleValues.class);
            if (body.isPresent()) {
                ConvertibleValues values = body.get();
                String component = bodyComponent.get();
                if (!values.contains(component)) {
                    component = NameUtils.hyphenate(component);
                }

                Optional<T> value = values.get(component, context);
                return newResult(value.orElse(null), context);
            } else {
                //noinspection unchecked
                return BindingResult.EMPTY;
            }
        } else {
            Optional<?> body = source.getBody();
            if (!body.isPresent()) {

                return BindingResult.EMPTY;
            } else {
                Object o = body.get();
                Optional<T> converted = conversionService.convert(o, context);
                return newResult(converted.orElse(null), context);
            }
        }
    }

    private BindingResult<T> newResult(T converted, ArgumentConversionContext<T> context) {
        final Optional<ConversionError> lastError = context.getLastError();
        //noinspection OptionalIsPresent
        if (lastError.isPresent()) {
            return new BindingResult<T>() {
                @Override
                public Optional<T> getValue() {
                    return Optional.empty();
                }

                @Override
                public List<ConversionError> getConversionErrors() {
                    return Collections.singletonList(lastError.get());
                }
            };
        } else {
            return () -> Optional.ofNullable(converted);
        }
    }
}
