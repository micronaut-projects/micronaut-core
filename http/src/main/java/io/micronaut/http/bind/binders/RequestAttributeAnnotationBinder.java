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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestAttribute;

/**
 * An {@link io.micronaut.core.bind.annotation.AnnotatedArgumentBinder} implementation that uses the {@link RequestAttribute}
 * annotation to trigger binding from an HTTP request attribute.
 * NOTE: The binder is annotating as postponed to allow injecting attributes added by filters.
 *
 * @param <T> A type
 * @author Ahmed Lafta
 */
public class RequestAttributeAnnotationBinder<T> extends AbstractArgumentBinder<T>
    implements AnnotatedRequestArgumentBinder<RequestAttribute, T>, PostponedRequestArgumentBinder<T> {

    /**
     * @param conversionService conversionService
     */
    public RequestAttributeAnnotationBinder(ConversionService conversionService) {
        super(conversionService);
    }

    /**
     * @param conversionService conversionService
     * @param argument argument
     */
    public RequestAttributeAnnotationBinder(ConversionService conversionService,
                                            Argument<T> argument) {
        super(conversionService, argument);
    }

    @Override
    public RequestArgumentBinder<T> createSpecific(Argument<T> argument) {
        return new RequestAttributeAnnotationBinder<>(conversionService, argument);
    }

    @Override
    public Class<RequestAttribute> getAnnotationType() {
        return RequestAttribute.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> argument, HttpRequest<?> source) {
        return doBind(argument, source.getAttributes(), BindingResult.unsatisfied());
    }

    @Override
    protected String getParameterName(Argument<T> argument) {
        return argument.getAnnotationMetadata().stringValue(RequestAttribute.class).orElse(argument.getName());
    }

    @Override
    protected String getFallbackFormat(Argument<?> argument) {
        return NameUtils.hyphenate(NameUtils.capitalize(argument.getName()), false);
    }
}
