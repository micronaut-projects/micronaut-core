/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * The runtime counterpart of the annotation mappers, transformers and remappers of the annotation
 * processors, for the metadata {@link ReflectionAnnotationMetadataBuilder} builds reflectively.
 *
 * <p>A processor extension that derives a member from the annotation type — the validator classes a
 * constraint is validated by, copied from its {@code @Constraint} meta-annotation — has to be applied to the
 * reflective metadata too, or the code reading the member behaves differently for a type the processor never
 * saw. An implementation is registered as a service and receives every annotation the builder converts,
 * stereotypes included, with a mutable copy of its values.</p>
 *
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
@Indexed(ReflectionAnnotationCustomizer.class)
public interface ReflectionAnnotationCustomizer {

    /**
     * @param annotationType The annotation type
     * @return Whether {@link #customize(Annotation, Map)} is to be called for the annotations of the type
     */
    default boolean supports(Class<? extends Annotation> annotationType) {
        return true;
    }

    /**
     * Customizes the values of an annotation before they enter the metadata.
     *
     * @param annotation The annotation
     * @param values     The values, as {@link ReflectionAnnotationMetadataBuilder#values(Annotation)} converts
     *                   them, to add to, replace or remove from
     */
    void customize(Annotation annotation, Map<CharSequence, Object> values);
}
