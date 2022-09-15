/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;

/**
 * Skips binding parts because they should be handled by a multipart processor.
 *
 * @param <T> The part type
 * @author James Kleeh
 * @since 3.6.4
 */
public class PartAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Part, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<Part, T> {

    public PartAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        //noinspection unchecked
        return BindingResult.UNSATISFIED;
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
