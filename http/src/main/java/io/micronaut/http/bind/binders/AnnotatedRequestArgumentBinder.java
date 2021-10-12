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

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;

import java.lang.annotation.Annotation;

/**
 * An interface for classes that bind an {@link io.micronaut.core.type.Argument} from an {@link HttpRequest} driven by
 * an annotation.
 *
 * @param <A> An annotation
 * @param <T> A type
 * @author Graeme Rocher
 * @see CookieAnnotationBinder
 * @see ParameterAnnotationBinder
 * @see HeaderAnnotationBinder
 * @see RequestAttributeAnnotationBinder
 * @since 1.0
 */
public interface AnnotatedRequestArgumentBinder<A extends Annotation, T> extends RequestArgumentBinder<T>, AnnotatedArgumentBinder<A, T, HttpRequest<?>> {

    /**
     * Create a binder from an annotation type and another binder.
     *
     * @param annotationType The annotation type
     * @param binder         The binder
     * @param <SA>           The annotation generic type
     * @param <ST>           The argument type
     * @return The {@link AnnotatedRequestArgumentBinder}
     */
    static <SA extends Annotation, ST> AnnotatedRequestArgumentBinder of(Class<SA> annotationType, ArgumentBinder<ST, HttpRequest<?>> binder) {
        return new AnnotatedRequestArgumentBinder<SA, ST>() {

            @Override
            public BindingResult<ST> bind(ArgumentConversionContext<ST> argument, HttpRequest<?> source) {
                return binder.bind(argument, source);
            }

            @Override
            public Class<SA> getAnnotationType() {
                return annotationType;
            }
        };
    }
}
