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

import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an annotation in the AST.
 *
 * @see io.micronaut.inject.visitor.VisitorContext#getClassElement(String)
 * @author graemerocher
 * @since 3.1.0
 */
public interface AnnotationElement extends ClassElement {

    /**
     * The element types an annotation interface that declares no {@link java.lang.annotation.Target} may be
     * written on: every declaration context and no type context, as JLS 9.6.4.1 specifies. That is every
     * {@link ElementType} except {@link ElementType#TYPE_USE} and {@link ElementType#TYPE_PARAMETER}.
     *
     * @since 5.3.0
     */
    Set<ElementType> DEFAULT_TARGETS = Collections.unmodifiableSet(
        EnumSet.complementOf(EnumSet.of(ElementType.TYPE_USE, ElementType.TYPE_PARAMETER))
    );

    /**
     * Is the annotation annotated with {@link java.lang.annotation.Inherited}?
     *
     * <p>Meta-annotations from the {@code java.lang.annotation} package are excluded from the
     * annotation metadata (see {@link io.micronaut.core.annotation.AnnotationUtil#STEREOTYPE_EXCLUDES}),
     * so the answer cannot be retrieved with
     * {@link io.micronaut.core.annotation.AnnotationMetadata#hasDeclaredAnnotation(String)}. Implementations
     * backed by a native element resolve it from that element instead; the default implementation, for
     * implementations that have no native element to inspect, always returns {@code false}.</p>
     *
     * @return {@code true} if the annotation type is declared {@link java.lang.annotation.Inherited}
     * @since 5.2.0
     */
    default boolean isInherited() {
        return false;
    }

    /**
     * The element types this annotation interface may be written on, as its
     * {@link java.lang.annotation.Target} (or {@code kotlin.annotation.Target}) declares them, mapped to
     * {@link ElementType}. When the interface declares no target the result is {@link #DEFAULT_TARGETS}.
     *
     * <p>Like {@link #isInherited()}, the answer is resolved from the native element, because
     * {@code java.lang.annotation} meta-annotations are excluded from the annotation metadata. The default
     * implementation, for implementations that have no native element to inspect, returns
     * {@link #DEFAULT_TARGETS}.</p>
     *
     * @return The element types the annotation may target; never {@code null}
     * @since 5.3.0
     */
    @NonNull
    default Set<ElementType> getTargets() {
        return DEFAULT_TARGETS;
    }

    /**
     * The annotation interface that holds the repetitions of this one, by binary name, when this annotation
     * is repeatable: declared with {@link java.lang.annotation.Repeatable}, {@code kotlin.annotation.Repeatable}
     * or {@code kotlin.jvm.JvmRepeatable} alike.
     *
     * <p>The default implementation, for implementations that have no native element to inspect, returns
     * empty.</p>
     *
     * @return The binary name of the container annotation, or empty when the annotation is not repeatable
     * @since 5.3.0
     */
    @NonNull
    default Optional<String> getRepeatableContainer() {
        return Optional.empty();
    }

    /**
     * The retention of this annotation interface, as its {@link java.lang.annotation.Retention} (or
     * {@code kotlin.annotation.Retention}) declares it; {@link RetentionPolicy#RUNTIME} when it declares none.
     *
     * <p>The default implementation, for implementations that have no native element to inspect, returns
     * {@link RetentionPolicy#RUNTIME}.</p>
     *
     * @return The retention policy; never {@code null}
     * @since 5.3.0
     */
    @NonNull
    default RetentionPolicy getRetentionPolicy() {
        return RetentionPolicy.RUNTIME;
    }
}
