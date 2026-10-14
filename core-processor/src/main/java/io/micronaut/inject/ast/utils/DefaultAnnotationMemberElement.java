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
package io.micronaut.inject.ast.utils;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.AnnotationMemberElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.MemberElement;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An {@link AnnotationMemberElement} over the element a compiler answers an annotation member as: a method
 * element under javac and the Groovy compiler, a property element under KSP.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultAnnotationMemberElement implements AnnotationMemberElement {

    private final MemberElement member;
    private final ClassElement type;
    private final Supplier<Map<CharSequence, Object>> defaultValues;

    /**
     * @param member        The element the compiler answers the member as
     * @param type          The declared type of the member
     * @param defaultValues The default values of the annotation by member name, as the metadata builder records them
     */
    public DefaultAnnotationMemberElement(MemberElement member,
                                          ClassElement type,
                                          Supplier<Map<CharSequence, Object>> defaultValues) {
        this.member = member;
        this.type = type;
        this.defaultValues = defaultValues;
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.ofNullable(defaultValues.get().get(member.getName()));
    }

    @Override
    public String getName() {
        return member.getName();
    }

    @Override
    public ClassElement getDeclaringType() {
        return member.getDeclaringType();
    }

    @Override
    public ClassElement getOwningType() {
        return member.getOwningType();
    }

    @Override
    public Object getNativeType() {
        return member.getNativeType();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return member.getAnnotationMetadata();
    }

    @Override
    public boolean isPackagePrivate() {
        return member.isPackagePrivate();
    }

    @Override
    public boolean isProtected() {
        return member.isProtected();
    }

    @Override
    public boolean isPublic() {
        return member.isPublic();
    }

    @Override
    public boolean isPrivate() {
        return member.isPrivate();
    }

    @Override
    public boolean isAbstract() {
        return member.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return member.isStatic();
    }

    @Override
    public boolean isFinal() {
        return member.isFinal();
    }

    @Override
    public boolean isSynthetic() {
        return member.isSynthetic();
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return member.getModifiers();
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        return member.getDocumentation(parseContent);
    }

    @Override
    public boolean isReflectionRequired(ClassElement callingType) {
        return member.isReflectionRequired(callingType);
    }

    @Override
    public boolean isAccessible(ClassElement callingType, boolean allowReflection) {
        return member.isAccessible(callingType, allowReflection);
    }

    @Override
    public boolean hides(MemberElement hidden) {
        return member.hides(hidden);
    }

    @Override
    public MemberElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new DefaultAnnotationMemberElement(member.withAnnotationMetadata(annotationMetadata), type, defaultValues);
    }

    @Override
    public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        member.annotate(annotationType, consumer);
        return this;
    }

    @Override
    public Element removeAnnotation(String annotationType) {
        member.removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotation(Class<T> annotationType) {
        member.removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        member.removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public Element removeStereotype(String annotationType) {
        member.removeStereotype(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeStereotype(Class<T> annotationType) {
        member.removeStereotype(annotationType);
        return this;
    }

    @Override
    public Element annotate(String annotationType) {
        member.annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        member.annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType) {
        member.annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
        member.annotate(annotationValue);
        return this;
    }

    @Override
    public String getDescription(boolean simple) {
        return member.getDescription(simple);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DefaultAnnotationMemberElement that && member.equals(that.member);
    }

    @Override
    public int hashCode() {
        return member.hashCode();
    }

    @Override
    public String toString() {
        return member.toString();
    }
}
