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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * An {@code AnnotationMapper} is a type that is capable of mapping a given annotation to one or many annotations at compile time.
 *
 * <p>Use cases include if you wish to support different annotation types or specifications but wish to map them a common internal annotation annotation.</p>
 *
 * <p>For example to support the JPA spec you may wish to map the {@code PersistenceContext} annotation to one or many other internal annotations with relying specifically on the JPA specification.</p>
 *
 * <p>AnnotationMapper instances can be registered with the service loader pattern via {@code META-INF/services} and will be picked up at compile time and should implement
 * either {@link NamedAnnotationMapper} or {@link TypedAnnotationMapper}</p>
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The annotation type
 * @see NamedAnnotationMapper
 * @see TypedAnnotationMapper
 */
public interface AnnotationMapper<T extends Annotation> {

    /**
     * The map method will be called for each instances of the annotation returned via this method.
     *
     * @param annotation The annotation values
     * @param visitorContext The context that is being visited
     * @return A list of zero or many annotations and values to map to
     */
    List<io.micronaut.core.annotation.AnnotationValue<?>> map(AnnotationValue<T> annotation, VisitorContext visitorContext);
}
