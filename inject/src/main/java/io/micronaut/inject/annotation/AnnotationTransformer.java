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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * An {@code AnnotationTransformer} transforms an annotation definition into one or many other annotation
 * definitions discarding the original annotation.
 *
 * <p>Unlike {@link AnnotationMapper} which retains the original annotation information this interface can
 * be used to optimize produced annotation metadata and discard unnecessary annotations.</p>
 *
 * @since 2.0
 * @author graemerocher
 * @see AnnotationMapper
 * @param <T> The annotation type
 */
public interface AnnotationTransformer<T extends Annotation> {

    /**
     * The transform method will be called for each instances of the annotation returned via this method.
     *
     * @param annotation The annotation values
     * @param visitorContext The context that is being visited
     * @return A list of zero or many annotations and values to map to
     */
    List<AnnotationValue<?>> transform(AnnotationValue<T> annotation, VisitorContext visitorContext);
}
