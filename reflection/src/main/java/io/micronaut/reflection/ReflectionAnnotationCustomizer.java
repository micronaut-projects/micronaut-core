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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.AnnotationValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * The runtime counterpart of the annotation mappers, transformers and remappers of the annotation processors,
 * for the metadata {@link ReflectionAnnotations} builds reflectively.
 *
 * <p>A processor extension that derives a member from the annotation type - the validator classes a constraint
 * is validated by, copied from its {@code @Constraint} meta-annotation - has to be applied to the reflective
 * metadata too, or the code reading the member behaves differently for a type the processor never saw. An
 * implementation is registered as a service and receives every annotation the builder converts, stereotypes
 * included, with a mutable copy of its values.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
@Indexed(ReflectionAnnotationCustomizer.class)
public interface ReflectionAnnotationCustomizer {

    /**
     * Whether the customizer applies to an annotation type.
     *
     * @param annotationType The annotation type
     * @return Whether {@link #customize(Annotation, Map)} is to be called for the annotations of the type
     */
    default boolean supports(Class<? extends Annotation> annotationType) {
        return true;
    }

    /**
     * Whether an annotation type is to be treated as {@link io.micronaut.core.annotation.Retainable}, so that
     * every annotation composing it keeps the composed occurrence in its own retained tree.
     *
     * <p>The compile-time counterpart is a remapper marking the contract of a family retainable - the validation
     * processor marks {@code jakarta.validation.Constraint}, so that a composed constraint keeps the constraints
     * it composes. A specification whose contract is an annotation it does not own cannot annotate it, and says
     * so here instead, so that the tree the reflective metadata carries is the tree the processors record.</p>
     *
     * @param annotationType The annotation type
     * @return Whether the annotations composing the type retain it
     */
    default boolean isRetainable(Class<? extends Annotation> annotationType) {
        return false;
    }

    /**
     * The member overrides a member of an annotation declares in terms other than {@link AliasFor}, stated as
     * the {@link AliasFor} annotations a transformer would produce for it at compilation time.
     *
     * <p>A specification declares an override in its own terms - {@code jakarta.validation.OverridesAttribute}
     * names the composed constraint and the member of it a member overrides - and the processors read it through
     * a transformer mapping it onto {@link AliasFor}. That transformer does not run at runtime, so the same
     * mapping is stated here, and the retained tree carries the overridden members whichever way the metadata
     * was built.</p>
     *
     * @param member The member of an annotation type
     * @return The aliases the member declares, empty when it declares none in other terms
     */
    default List<AnnotationValue<AliasFor>> aliasesOf(Method member) {
        return List.of();
    }

    /**
     * Customizes the values of an annotation before they enter the metadata.
     *
     * @param annotation The annotation
     * @param values     The values, as {@link ReflectionAnnotations#values(Annotation)} converts them, to add to,
     *                   replace or remove from
     */
    void customize(Annotation annotation, Map<CharSequence, Object> values);
}
