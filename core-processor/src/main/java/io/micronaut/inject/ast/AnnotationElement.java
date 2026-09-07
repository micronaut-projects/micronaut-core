/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationUtil;

/**
 * Represents an annotation in the AST.
 *
 * @see io.micronaut.inject.visitor.VisitorContext#getClassElement(String)
 * @author graemerocher
 * @since 3.1.0
 */
public interface AnnotationElement extends ClassElement {

    /**
     * Is the annotation annotated with {@link java.lang.annotation.Inherited}?
     *
     * <p>Meta-annotations from the {@code java.lang.annotation} package are excluded from the
     * annotation metadata (see {@link AnnotationUtil#STEREOTYPE_EXCLUDES}), so the answer cannot be
     * retrieved with {@link io.micronaut.core.annotation.AnnotationMetadata#hasDeclaredAnnotation(String)}.
     * This method resolves it from the underlying native element instead.</p>
     *
     * @return {@code true} if the annotation type is declared {@link java.lang.annotation.Inherited}
     * @since 5.2.0
     */
    default boolean isInherited() {
        return getAnnotationMetadata().hasDeclaredAnnotation(AnnotationUtil.ANN_INHERITED);
    }
}
