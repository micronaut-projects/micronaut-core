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
package io.micronaut.http.client.bind;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Annotation;

/**
 * An interface for classes that bind an {@link io.micronaut.core.type.Argument} to an
 * {@link io.micronaut.http.MutableHttpRequest} driven by an annotation.
 *
 * @param <A> An annotation
 * @param <T> A type
 * @author James Kleeh
 * @since 2.1.0
 */
@Experimental
public interface AnnotatedClientArgumentRequestBinder<A extends Annotation, T> extends ClientArgumentRequestBinder<T> {

    /**
     * @return The annotation type.
     */
    Class<A> getAnnotationType();
}
