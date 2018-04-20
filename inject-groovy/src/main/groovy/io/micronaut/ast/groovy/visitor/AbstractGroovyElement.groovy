package io.micronaut.ast.groovy.visitor

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.visitor.Element

import javax.annotation.Nullable
import java.lang.annotation.Annotation

abstract class AbstractGroovyElement implements AnnotationMetadata, Element {

    private final AnnotationMetadata annotationMetadata

    AbstractGroovyElement(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata
    }

    @Override
    boolean hasDeclaredAnnotation(@Nullable String annotation) {
        annotationMetadata.hasDeclaredAnnotation(annotation)
    }

    @Override
    boolean hasAnnotation(@Nullable String annotation) {
        annotationMetadata.hasAnnotation(annotation)
    }

    @Override
    boolean hasStereotype(@Nullable String annotation) {
        annotationMetadata.hasStereotype(annotation)
    }

    @Override
    boolean hasDeclaredStereotype(@Nullable String annotation) {
        annotationMetadata.hasDeclaredStereotype(annotation)
    }

    @Override
    Set<String> getAnnotationNamesByStereotype(String stereotype) {
        annotationMetadata.getAnnotationNamesByStereotype(stereotype)
    }

    @Override
    Set<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        annotationMetadata.getDeclaredAnnotationNamesTypeByStereotype(stereotype)
    }

    @Override
    ConvertibleValues<Object> getValues(String annotation) {
        annotationMetadata.getValues(annotation)
    }

    @Override
    ConvertibleValues<Object> getDeclaredValues(String annotation) {
        annotationMetadata.getDeclaredValues(annotation)
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        annotationMetadata.getValues(annotation, valueType)
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        annotationMetadata.getDefaultValue(annotation, member, requiredType)
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        annotationMetadata.getDefaultValue(annotation, member, requiredType)
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        annotationMetadata.getValue(annotation, member, requiredType)
    }

    @Override
    Optional<String> getAnnotationNameByStereotype(String stereotype) {
        annotationMetadata.getAnnotationNameByStereotype(stereotype)
    }

    @Override
    Optional<String> getDeclaredAnnotationNameTypeByStereotype(String stereotype) {
        annotationMetadata.getDeclaredAnnotationNameTypeByStereotype(stereotype)
    }

    @Override
    Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        annotationMetadata.getAnnotationTypeByStereotype(stereotype)
    }

    @Override
    Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        annotationMetadata.getDeclaredAnnotationTypeByStereotype(stereotype)
    }

    @Override
    Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        annotationMetadata.getDeclaredAnnotationTypeByStereotype(stereotype)
    }

    @Override
    Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
        annotationMetadata.getAnnotationTypeByStereotype(stereotype)
    }

    @Override
    Optional<String> getAnnotationNameByStereotype(Class<? extends Annotation> stereotype) {
        annotationMetadata.getAnnotationNameByStereotype(stereotype)
    }

    @Override
    public <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation, Class<T> valueType) {
        annotationMetadata.getValues(annotation, valueType)
    }

    @Override
    Set<String> getAnnotationNamesByStereotype(Class<? extends Annotation> stereotype) {
        annotationMetadata.getAnnotationNamesByStereotype(stereotype)
    }

    @Override
    Set<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype) {
        annotationMetadata.getAnnotationTypesByStereotype(stereotype)
    }

    @Override
    ConvertibleValues<Object> getValues(Class<? extends Annotation> annotation) {
        annotationMetadata.getValues(annotation)
    }

    @Override
    <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        annotationMetadata.getValue(annotation, member, requiredType)
    }

    @Override
    OptionalLong longValue(String annotation, String member) {
        annotationMetadata.longValue(annotation, member)
    }

    @Override
    Optional<Class> classValue(String annotation) {
        annotationMetadata.classValue(annotation)
    }

    @Override
    Optional<Class> classValue(String annotation, String member) {
        annotationMetadata.classValue(annotation, member)
    }

    @Override
    Optional<Class> classValue(Class<? extends Annotation> annotation) {
        annotationMetadata.classValue(annotation)
    }

    @Override
    Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        annotationMetadata.classValue(annotation, member)
    }

    @Override
    OptionalInt intValue(String annotation, String member) {
        annotationMetadata.intValue(annotation, member)
    }

    @Override
    OptionalDouble doubleValue(String annotation, String member) {
        annotationMetadata.doubleValue(annotation, member)
    }

    @Override
    <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        annotationMetadata.getValue(annotation, requiredType)
    }

    @Override
    Optional<Object> getValue(String annotation, String member) {
        annotationMetadata.getValue(annotation, member)
    }

    @Override
    Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        annotationMetadata.getValue(annotation, member)
    }

    @Override
    boolean isTrue(String annotation, String member) {
        annotationMetadata.isTrue(annotation, member)
    }

    @Override
    boolean isTrue(Class<? extends Annotation> annotation, String member) {
        annotationMetadata.isTrue(annotation, member)
    }

    @Override
    boolean isPresent(String annotation, String member) {
        annotationMetadata.isPresent(annotation, member)
    }

    @Override
    boolean isPresent(Class<? extends Annotation> annotation, String member) {
        annotationMetadata.isPresent(annotation, member)
    }

    @Override
    boolean isFalse(Class<? extends Annotation> annotation, String member) {
        annotationMetadata.isFalse(annotation, member)
    }

    @Override
    boolean isFalse(String annotation, String member) {
        annotationMetadata.isFalse(annotation, member)
    }

    @Override
    Optional<Object> getValue(String annotation) {
        annotationMetadata.getValue(annotation)
    }

    @Override
    Optional<Object> getValue(Class<? extends Annotation> annotation) {
        annotationMetadata.getValue(annotation)
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        annotationMetadata.getValue(annotation, requiredType)
    }

    @Override
    boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        annotationMetadata.hasAnnotation(annotation)
    }

    @Override
    boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        annotationMetadata.hasStereotype(annotation)
    }

    @Override
    boolean hasStereotype(Class<? extends Annotation>... annotations) {
        annotationMetadata.hasStereotype(annotations)
    }

    @Override
    boolean hasStereotype(String[] annotations) {
        annotationMetadata.hasStereotype(annotations)
    }

    @Override
    boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        annotationMetadata.hasDeclaredAnnotation(annotation)
    }

    @Override
    boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        annotationMetadata.hasDeclaredStereotype(stereotype)
    }

    @Override
    boolean hasDeclaredStereotype(Class<? extends Annotation>... annotations) {
        annotationMetadata.hasDeclaredStereotype(annotations)
    }

    @Override
    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        annotationMetadata.isAnnotationPresent(annotationClass)
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        annotationMetadata.getAnnotation(annotationClass)
    }

    @Override
    Annotation[] getAnnotations() {
        annotationMetadata.getAnnotations()
    }

    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        annotationMetadata.getAnnotationsByType(annotationClass)
    }

    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        annotationMetadata.getDeclaredAnnotation(annotationClass)
    }

    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        annotationMetadata.getDeclaredAnnotationsByType(annotationClass)
    }

    @Override
    Annotation[] getDeclaredAnnotations() {
        annotationMetadata.getDeclaredAnnotations()
    }
}
