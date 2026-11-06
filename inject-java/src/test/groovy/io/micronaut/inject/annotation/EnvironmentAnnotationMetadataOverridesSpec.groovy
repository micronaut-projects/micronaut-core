package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Guards {@link AbstractEnvironmentAnnotationMetadata} against inheriting a default method of
 * {@link AnnotationMetadata} that answers on its own instead of calling the methods the class overrides.
 */
class EnvironmentAnnotationMetadataOverridesSpec extends Specification {

    /**
     * The default methods whose body only calls other methods of the metadata, which the class overrides.
     * Check the body of a default method before adding it: a default that returns a constant must be overridden.
     */
    private static final Set<String> DELEGATING_DEFAULTS = [
            'booleanValue(Class)',
            'booleanValue(String)',
            'classValue(Class)',
            'classValue(String)',
            'classValues(Class)',
            'classValues(String)',
            'doubleValue(Class)',
            'enumValue(Class, Class)',
            'enumValue(String, Class)',
            'enumValues(Class, Class)',
            'enumValues(Class, String, Class)',
            'enumValues(String, Class)',
            'enumValuesSet(Class, String, Class)',
            'enumValuesSet(String, String, Class)',
            'findAnnotation(Class)',
            'findDeclaredAnnotation(Class)',
            'getAnnotation(Class)',
            'getAnnotation(String)',
            'getAnnotationNameByStereotype(Class)',
            'getAnnotationNameByStereotype(String)',
            'getAnnotationNamesByStereotype(Class)',
            'getAnnotationTypesByStereotype(Class)',
            'getAnnotationTypesByStereotype(Class, ClassLoader)',
            'getAnnotationTypesByStereotype(String)',
            'getDeclaredAnnotation(Class)',
            'getDeclaredAnnotation(String)',
            'getDeclaredAnnotationNameByStereotype(String)',
            'getDeclaredAnnotationTypeByStereotype(Class)',
            'getDeclaredAnnotationTypeByStereotype(String)',
            'getDefaultValue(Class, String, Argument)',
            'getDefaultValue(Class, String, Class)',
            'getValue(Class)',
            'getValue(Class, Argument)',
            'getValue(Class, Class)',
            'getValue(Class, String)',
            'getValue(Class, String, Argument)',
            'getValue(Class, String, Class)',
            'getValue(String)',
            'getValue(String, Argument)',
            'getValue(String, Class)',
            'getValue(String, String)',
            'getValue(String, String, Class)',
            'getValues(Class, Class)',
            'getValues(String)',
            'hasAnnotation(Class)',
            'hasDeclaredAnnotation(Class)',
            'hasDeclaredStereotype(Class)',
            'hasDeclaredStereotype(Class[])',
            'hasDeclaredStereotype(String[])',
            'hasSimpleAnnotation(String)',
            'hasSimpleDeclaredAnnotation(String)',
            'hasStereotype(Class)',
            'hasStereotype(Class[])',
            'hasStereotype(String[])',
            'hasStereotypeNonRepeating(Class)',
            'intValue(Class)',
            'isAnnotationPresent(Class)',
            'isAnnotationPresent(String)',
            'isDeclaredAnnotationPresent(Class)',
            'isDeclaredAnnotationPresent(String)',
            'isPresent(Class, String)',
            'isPresent(String, String)',
            'stringValue(Class)',
            'stringValue(String)',
            'stringValues(Class)',
            'stringValues(String)'
    ] as Set

    void "test every default method of AnnotationMetadata is overridden or only delegates to overridden methods"() {
        when:
        Set<String> inherited = AnnotationMetadata.methods
                .findAll { it.isDefault() }
                .findAll { AbstractEnvironmentAnnotationMetadata.getMethod(it.name, it.parameterTypes).declaringClass.isInterface() }
                .collect { signature(it) } as Set

        then: "a new default method must be overridden, or added to the allow-list when it only calls overridden methods"
        (inherited - DELEGATING_DEFAULTS).isEmpty()

        and: "an allow-listed method that is now overridden must be removed from the allow-list"
        (DELEGATING_DEFAULTS - inherited).isEmpty()
    }

    private static String signature(Method method) {
        method.name + '(' + method.parameterTypes*.simpleName.join(', ') + ')'
    }
}
