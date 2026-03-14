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
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Binds a String body argument.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultBodyAnnotationBinder<T> extends AbstractArgumentBinder<T> implements BodyArgumentBinder<T> {

    protected final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        super(conversionService);
        this.conversionService = conversionService;
    }

    @Override
    public final Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public final BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (!source.getMethod().permitsRequestBody()) {
            return BindingResult.unsatisfied();
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
            return bindBodyPart(context, source, bodyComponent);
        } else {
            return bindFullBody(context, source);
        }
    }

    /**
     * Bind a <i>part</i> of the body, for argument spreading. By default, this gets the argument
     * from {@link #bindFullBodyConvertibleValues(HttpRequest)}.
     *
     * @param context       The context to convert with
     * @param source        The request
     * @param bodyComponent The name of the component to bind to
     * @return The binding result
     */
    protected BindingResult<T> bindBodyPart(ArgumentConversionContext<T> context, HttpRequest<?> source, String bodyComponent) {
        return bindFullBodyConvertibleValues(source).flatMap(cv -> doBind(context, cv, bodyComponent));
    }

    /**
     * Try to bind from the <i>full</i> body of the request to a {@link ConvertibleValues} for
     * argument spreading.
     *
     * @param source The request
     * @return The body as a {@link ConvertibleValues} instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        Optional<ConvertibleValues> convertibleValuesBody = source.getBody(ConvertibleValues.class);
        return () -> (Optional) convertibleValuesBody;
    }

    /**
     * Try to bind from the <i>full</i> body of the request, i.e. no argument spreading.
     *
     * @param context The conversion context
     * @param source  The request
     * @return The binding result
     */
    public BindingResult<T> bindFullBody(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Optional<?> body = source.getBody();
        if (body.isPresent()) {
            return doConvert(body.get(), context);
        }
        return bindDefaultValue(context);
    }

    /**
     * Try to bind the <i>defaultValue</i>, if any specified.
     *
     * @param context The conversion context
     * @return The binding result
     */
    protected BindingResult<T> bindDefaultValue(ArgumentConversionContext<T> context) {
        Optional<String> defaultValue = context.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue");
        return defaultValue.isPresent() ? doConvert(defaultValue.get(), context) : BindingResult.empty();
    }
}
