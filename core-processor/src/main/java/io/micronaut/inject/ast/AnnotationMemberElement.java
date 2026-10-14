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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;

import java.util.Optional;

/**
 * A member of an annotation interface: a method of a Java or Groovy annotation interface, a property of a
 * Kotlin annotation class.
 *
 * <p>The member is a {@link MemberElement} of the {@link AnnotationElement} that declares it, so its own
 * annotations (a {@code @NonBinding}, say) are read through {@link #getAnnotationMetadata()} like any other
 * member's, and {@link #getDeclaringType()} is the annotation interface.</p>
 *
 * @author Denis Stepanov
 * @see AnnotationElement#getMembers()
 * @since 5.3.0
 */
public interface AnnotationMemberElement extends MemberElement, TypedElement {

    /**
     * The type the member is declared with: the return type of the method, the type of the property.
     *
     * @return The declared type of the member; never {@code null}
     * @since 5.3.0
     */
    @Override
    ClassElement getType();

    /**
     * The default value the member declares, in the form the annotation metadata records a value of the
     * member: a boxed primitive, a {@link String}, the name of an enum constant, an
     * {@link io.micronaut.core.annotation.AnnotationClassValue} for a class, an
     * {@link io.micronaut.core.annotation.AnnotationValue} for a nested annotation, or an array of those.
     *
     * <p>The value is the one {@link io.micronaut.inject.visitor.VisitorContext#getAnnotationDefaultValues(String)}
     * answers for the member, so an empty string default is reported. Under KSP the defaults of an annotation
     * class are only visible on a use of the annotation, so a member of a Kotlin annotation that no element of the
     * compilation is annotated with reports no default.</p>
     *
     * @return The default value, or empty when the member declares none or it cannot be resolved
     * @since 5.3.0
     */
    Optional<Object> getDefaultValue();

    /**
     * The annotation metadata of the member itself: what is written on the method or property, not what is
     * written on the annotation interface that declares it.
     *
     * @return The annotation metadata of the member
     * @since 5.3.0
     */
    @Override
    AnnotationMetadata getAnnotationMetadata();

    @Override
    default AnnotationMemberElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (AnnotationMemberElement) MemberElement.super.withAnnotationMetadata(annotationMetadata);
    }
}
