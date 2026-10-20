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
package io.micronaut.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The description of an annotation type, written at compile time: its members with their types, defaults and
 * annotations, its retention, targets and repeatable container, and the annotations written on the annotation type
 * itself, processed as any other annotation metadata is.
 *
 * <p>A description is written with the {@link AnnotationBuilder} of the annotation type, so it is available for the
 * types listed in {@link RegisterAnnotations} and those a visitor registers a builder for, and is found the way the
 * builder is, with {@link AnnotationBuilder#describe()} or {@link #find(Class, ClassLoader)}. A builder written by an
 * earlier processor has no description.</p>
 *
 * <p>The {@code java.lang.annotation} meta-annotations are left out of the {@link #getAnnotationMetadata() annotation
 * metadata} as everywhere else; {@link #getRetentionPolicy()}, {@link #getTargets()}, {@link #isInherited()} and
 * {@link #getRepeatableContainer()} answer what they declare.</p>
 *
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AnnotationType<A extends Annotation> extends AnnotationMetadataProvider {

    /**
     * Finds the description of an annotation type, among the builders visible to a class loader. Every call looks
     * the builders of the class loader up, so keep the description.
     *
     * @param annotationType The annotation type
     * @param classLoader    The class loader
     * @param <A>            The annotation type
     * @return The description, empty when no builder with a description is registered for this very type
     */
    static <A extends Annotation> Optional<AnnotationType<A>> find(Class<A> annotationType, ClassLoader classLoader) {
        return AnnotationBuilder.find(annotationType, classLoader).flatMap(AnnotationBuilder::describe);
    }

    /**
     * Finds the description of an annotation type by name, among the builders visible to a class loader. Every call
     * looks the builders of the class loader up, so keep the description.
     *
     * @param annotationName The name of the annotation type
     * @param classLoader    The class loader
     * @return The description, empty when no builder with a description is registered for the name
     */
    static Optional<AnnotationType<?>> find(String annotationName, ClassLoader classLoader) {
        return AnnotationBuilder.find(annotationName, classLoader).flatMap(AnnotationType::describe);
    }

    /**
     * @return The annotation type
     */
    Class<A> getType();

    /**
     * The members the annotation type declares, in declaration order.
     *
     * @return The members
     */
    List<AnnotationTypeMember<?>> getMembers();

    /**
     * Finds a member by name.
     *
     * @param name The name of the member
     * @return The member, empty when the annotation type declares none of that name
     */
    default Optional<AnnotationTypeMember<?>> findMember(String name) {
        for (AnnotationTypeMember<?> member : getMembers()) {
            if (member.getName().equals(name)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    /**
     * The annotation type that holds the repetitions of this one, by binary name, when the annotation type is
     * repeatable.
     *
     * @return The name of the container, empty when the annotation type is not repeatable
     */
    Optional<String> getRepeatableContainer();

    /**
     * @return The retention the annotation type declares, {@link RetentionPolicy#RUNTIME} when it declares none
     */
    RetentionPolicy getRetentionPolicy();

    /**
     * The element types the annotation can be written on. An annotation type that declares no target can be written
     * on every declaration and no type use.
     *
     * @return The element types
     */
    Set<ElementType> getTargets();

    /**
     * @return Whether the annotation type is declared {@link java.lang.annotation.Inherited}
     */
    boolean isInherited();

    /**
     * The annotations written on the annotation type, processed as any other annotation metadata is: the stereotypes
     * it is meta-annotated with, mapped and remapped, without the {@code java.lang.annotation} meta-annotations. The
     * annotations of a member are read from the {@link AnnotationTypeMember#getAnnotationMetadata() member}.
     *
     * @return The annotation metadata of the annotation type
     */
    @Override
    AnnotationMetadata getAnnotationMetadata();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<AnnotationType<?>> describe(AnnotationBuilder<?> builder) {
        return (Optional) builder.describe();
    }
}
