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
package org.particleframework.http.server.binding.binders;

import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.bind.annotation.AnnotatedArgumentBinder;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.http.HttpRequest;
import org.particleframework.core.type.Argument;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * An interface for classes that bind an {@link Argument} from an {@link HttpRequest} driven by an annotation
 *
 * @see CookieAnnotationBinder
 * @see ParameterAnnotationBinder
 * @see HeaderAnnotationBinder
 * 
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotatedRequestArgumentBinder<A extends Annotation, T> extends RequestArgumentBinder<T>, AnnotatedArgumentBinder<A, T, HttpRequest<?>> {

    /**
     * Create a binder from an annotation type and another binder
     *
     * @param annotationType The annotation type
     * @param binder The binder
     * @param <SA> The annotation generic type
     * @param <ST> The argument type
     * @return The {@link AnnotatedRequestArgumentBinder}
     */
    static <SA extends Annotation, ST> AnnotatedRequestArgumentBinder of(Class<SA> annotationType, ArgumentBinder<ST, HttpRequest<?>> binder) {
        return new AnnotatedRequestArgumentBinder<SA, ST>() {

            @Override
            public Optional<ST> bind(ArgumentConversionContext<ST> argument, HttpRequest<?> source) {
                return binder.bind(argument, source);
            }

            @Override
            public Class<SA> getAnnotationType() {
                return annotationType;
            }
        };
    }
}
