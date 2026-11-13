package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Guards {@link MappingAnnotationMetadataDelegate} against inheriting a method of {@link AnnotationMetadata} that
 * returns the annotation values of the target metadata without mapping them.
 */
class MappingAnnotationMetadataDelegateOverridesSpec extends Specification {

    /**
     * The methods left to {@link io.micronaut.core.annotation.AnnotationMetadataDelegate} or to the defaults of
     * {@link AnnotationMetadata}: they return no annotation value, or only call methods that the class overrides.
     * Check what a method returns before adding it: a method that returns the annotation values of the target,
     * or metadata that does, must be overridden.
     */
    private static final Set<String> UNMAPPED = [
            // the unmapped metadata, on purpose
            'copyAnnotationMetadata()',
            'getTargetAnnotationMetadata()',
            // the defaults of an annotation type
            'getDefaultValue(Class, String, Argument)',
            'getDefaultValue(Class, String, Class)',
            'getDefaultValue(String, String, Argument)',
            'getDefaultValue(String, String, Class)',
            'getDefaultValues(String)',
            // defaults that call enumValues, which is overridden
            'enumValuesSet(Class, String, Class)',
            'enumValuesSet(String, String, Class)',
            // names, types and presence
            'findRepeatableAnnotation(Class)',
            'findRepeatableAnnotation(String)',
            'getAnnotationNameByStereotype(Class)',
            'getAnnotationNameByStereotype(String)',
            'getAnnotationNames()',
            'getAnnotationNamesByStereotype(Class)',
            'getAnnotationNamesByStereotype(String)',
            'getAnnotationType(String)',
            'getAnnotationType(String, ClassLoader)',
            'getAnnotationTypeByStereotype(Class)',
            'getAnnotationTypeByStereotype(String)',
            'getAnnotationTypesByStereotype(Class)',
            'getAnnotationTypesByStereotype(Class, ClassLoader)',
            'getAnnotationTypesByStereotype(String)',
            'getDeclaredAnnotationNameByStereotype(String)',
            'getDeclaredAnnotationNames()',
            'getDeclaredAnnotationNamesByStereotype(String)',
            'getDeclaredAnnotationTypeByStereotype(Class)',
            'getDeclaredAnnotationTypeByStereotype(String)',
            'getDeclaredStereotypeAnnotationNames()',
            'getStereotypeAnnotationNames()',
            'hasAnnotation(Class)',
            'hasAnnotation(String)',
            'hasDeclaredAnnotation(Class)',
            'hasDeclaredAnnotation(String)',
            'hasDeclaredStereotype(Class)',
            'hasDeclaredStereotype(Class[])',
            'hasDeclaredStereotype(String)',
            'hasDeclaredStereotype(String[])',
            'hasPropertyExpressions()',
            'hasSimpleAnnotation(String)',
            'hasSimpleDeclaredAnnotation(String)',
            'hasStereotype(Class)',
            'hasStereotype(Class[])',
            'hasStereotype(String)',
            'hasStereotype(String[])',
            'hasStereotypeNonRepeating(Class)',
            'isAnnotationPresent(Class)',
            'isAnnotationPresent(String)',
            'isDeclaredAnnotationPresent(Class)',
            'isDeclaredAnnotationPresent(String)',
            'isEmpty()',
            'isPresent(Class, String)',
            'isPresent(String, String)',
            'isRepeatableAnnotation(Class)',
            'isRepeatableAnnotation(String)'
    ] as Set

    void "test every method of AnnotationMetadata is overridden or returns no annotation value of the target"() {
        when:
        Set<String> inherited = AnnotationMetadata.methods
                .findAll { EvaluatedAnnotationMetadata.getMethod(it.name, it.parameterTypes).declaringClass.isInterface() }
                .collect { signature(it) } as Set

        then: "a new method must be overridden, or added to the allow-list when it returns no annotation value of the target"
        (inherited - UNMAPPED).isEmpty()

        and: "an allow-listed method that is now overridden must be removed from the allow-list"
        (UNMAPPED - inherited).isEmpty()
    }

    private static String signature(Method method) {
        method.name + '(' + method.parameterTypes*.simpleName.join(', ') + ')'
    }
}
