/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * An abstract class for other elements to extend from.
 *
 * @author James Kleeh
 * @since 1.0
 */
public abstract class AbstractJavaElement implements io.micronaut.inject.visitor.Element, AnnotationMetadata {

    private final Element element;
    private final AnnotationMetadata annotationMetadata;

    AbstractJavaElement(Element element, AnnotationMetadata annotationMetadata) {
        this.element = element;
        this.annotationMetadata = annotationMetadata;
    }

    private boolean hasModifier(Modifier modifier) {
        return element.getModifiers().contains(modifier);
    }

    @Override
    public String getName() {
        return element.getSimpleName().toString();
    }

    @Override
    public boolean isAbstract() {
        return hasModifier(Modifier.ABSTRACT);
    }

    @Override
    public boolean isStatic() {
        return hasModifier(Modifier.STATIC);
    }

    @Override
    public boolean isPublic() {
        return hasModifier(Modifier.PUBLIC);
    }

    @Override
    public boolean isPrivate() {
        return hasModifier(Modifier.PRIVATE);
    }

    @Override
    public boolean isFinal() {
        return hasModifier(Modifier.FINAL);
    }

    @Override
    public boolean isProtected() {
        return hasModifier(Modifier.PROTECTED);
    }

    @Override
    public Object getNativeType() {
        return element;
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return annotationMetadata.hasStereotype(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredStereotype(annotation);
    }

    @Override
    public Set<String> getAnnotationNamesByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public Set<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        return annotationMetadata.getDeclaredAnnotationNamesTypeByStereotype(stereotype);
    }

    @Override
    public ConvertibleValues<Object> getValues(String annotation) {
        return annotationMetadata.getValues(annotation);
    }

    @Override
    public ConvertibleValues<Object> getDeclaredValues(String annotation) {
        return annotationMetadata.getDeclaredValues(annotation);
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        return annotationMetadata.getValues(annotation, valueType);
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return annotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return annotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return annotationMetadata.getValue(annotation, member, requiredType);
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationNameByStereotype(stereotype);
    }

    @Override
    public Optional<String> getDeclaredAnnotationNameTypeByStereotype(String stereotype) {
        return annotationMetadata.getDeclaredAnnotationNameTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return annotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return annotationMetadata.getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        return annotationMetadata.getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(Class<? extends Annotation> stereotype) {
        return annotationMetadata.getAnnotationNameByStereotype(stereotype);
    }

    @Override
    public <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation, Class<T> valueType) {
        return annotationMetadata.getValues(annotation, valueType);
    }

    @Override
    public Set<String> getAnnotationNamesByStereotype(Class<? extends Annotation> stereotype) {
        return annotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public Set<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype) {
        return annotationMetadata.getAnnotationTypesByStereotype(stereotype);
    }

    @Override
    public ConvertibleValues<Object> getValues(Class<? extends Annotation> annotation) {
        return annotationMetadata.getValues(annotation);
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        return annotationMetadata.getValue(annotation, member, requiredType);
    }

    @Override
    public OptionalLong longValue(String annotation, String member) {
        return annotationMetadata.longValue(annotation, member);
    }

    @Override
    public Optional<Class> classValue(String annotation) {
        return annotationMetadata.classValue(annotation);
    }

    @Override
    public Optional<Class> classValue(String annotation, String member) {
        return annotationMetadata.classValue(annotation, member);
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation) {
        return annotationMetadata.classValue(annotation);
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return annotationMetadata.classValue(annotation, member);
    }

    @Override
    public OptionalInt intValue(String annotation, String member) {
        return annotationMetadata.intValue(annotation, member);
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member) {
        return annotationMetadata.doubleValue(annotation, member);
    }

    @Override
    public <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        return annotationMetadata.getValue(annotation, requiredType);
    }

    @Override
    public Optional<Object> getValue(String annotation, String member) {
        return annotationMetadata.getValue(annotation, member);
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        return annotationMetadata.getValue(annotation, member);
    }

    @Override
    public boolean isTrue(String annotation, String member) {
        return annotationMetadata.isTrue(annotation, member);
    }

    @Override
    public boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return annotationMetadata.isTrue(annotation, member);
    }

    @Override
    public boolean isPresent(String annotation, String member) {
        return annotationMetadata.isPresent(annotation, member);
    }

    @Override
    public boolean isPresent(Class<? extends Annotation> annotation, String member) {
        return annotationMetadata.isPresent(annotation, member);
    }

    @Override
    public boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return annotationMetadata.isFalse(annotation, member);
    }

    @Override
    public boolean isFalse(String annotation, String member) {
        return annotationMetadata.isFalse(annotation, member);
    }

    @Override
    public Optional<Object> getValue(String annotation) {
        return annotationMetadata.getValue(annotation);
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation) {
        return annotationMetadata.getValue(annotation);
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        return annotationMetadata.getValue(annotation, requiredType);
    }

    @Override
    public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return annotationMetadata.hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        return annotationMetadata.hasStereotype(annotation);
    }

    @Override
    public boolean hasStereotype(Class<? extends Annotation>... annotations) {
        return annotationMetadata.hasStereotype(annotations);
    }

    @Override
    public boolean hasStereotype(String[] annotations) {
        return annotationMetadata.hasStereotype(annotations);
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return annotationMetadata.hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        return annotationMetadata.hasDeclaredStereotype(stereotype);
    }

    @Override
    public boolean hasDeclaredStereotype(Class<? extends Annotation>... annotations) {
        return annotationMetadata.hasDeclaredStereotype(annotations);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationMetadata.isAnnotationPresent(annotationClass);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return annotationMetadata.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return annotationMetadata.getAnnotations();
    }

    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        return annotationMetadata.getAnnotationsByType(annotationClass);
    }

    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return annotationMetadata.getDeclaredAnnotation(annotationClass);
    }

    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        return annotationMetadata.getDeclaredAnnotationsByType(annotationClass);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return annotationMetadata.getDeclaredAnnotations();
    }
}
