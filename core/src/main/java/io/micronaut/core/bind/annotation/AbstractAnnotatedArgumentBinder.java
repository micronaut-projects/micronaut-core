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
package io.micronaut.core.bind.annotation;

import io.micronaut.core.convert.ConversionService;

import java.lang.annotation.Annotation;

/**
 * An abstract {@link AnnotatedArgumentBinder} implementation.
 *
 * @param <A> The annotation type
 * @param <T> The argument type
 * @param <S> The binding source type
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link AbstractArgumentBinder}
 */
@Deprecated(forRemoval = true, since = "4.0")
public abstract class AbstractAnnotatedArgumentBinder<A extends Annotation, T, S>  extends AbstractArgumentBinder<T> implements AnnotatedArgumentBinder<A, T, S> {

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    protected AbstractAnnotatedArgumentBinder(ConversionService conversionService) {
        super(conversionService);
    }
}
