package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection

class JavacAnnotationMetadataParitySpec extends AbstractTypeElementSpec {

    void "a record-component-only annotation is present on the reflective property"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.ComponentBean", '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Introspected
record ComponentBean(@ComponentMark("component") String name) {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
@interface ComponentMark {
    String value();
}
''')
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "javac records the annotation on the generated property"
        generated.getRequiredProperty("name", String).annotationMetadata
            .stringValue("test.ComponentMark").orElse(null) == "component"

        and:
        reflective.getRequiredProperty("name", String).annotationMetadata
            .stringValue("test.ComponentMark").orElse(null) ==
            generated.getRequiredProperty("name", String).annotationMetadata
                .stringValue("test.ComponentMark").orElse(null)
    }

    void "a class type-parameter annotation is present on the reflective property argument"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.GenericBean", '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Introspected
class GenericBean<@TypeParameterMark("T") T> {
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_PARAMETER)
@interface TypeParameterMark {
    String value();
}
''')
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect:
        generated.getRequiredProperty("value", Object).asArgument().annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) == "T"

        and:
        reflective.getRequiredProperty("value", Object).asArgument().annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) ==
            generated.getRequiredProperty("value", Object).asArgument().annotationMetadata
                .stringValue("test.TypeParameterMark").orElse(null)
    }

    void "a method type-parameter annotation is present on reflective argument and return metadata"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.MethodBean", '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Introspected
class MethodBean {
    @Executable
    public <@TypeParameterMark("E") E> E echo(E value) {
        return value;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_PARAMETER)
@interface TypeParameterMark {
    String value();
}
''')
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)
        def generatedMethod = generated.beanMethods.find { it.name == "echo" }
        def reflectedMethod = reflective.beanMethods.find { it.name == "echo" }

        expect:
        generatedMethod.arguments[0].annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) == "E"
        generatedMethod.returnType.asArgument().annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) == "E"

        and:
        reflectedMethod.arguments[0].annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) ==
            generatedMethod.arguments[0].annotationMetadata
                .stringValue("test.TypeParameterMark").orElse(null)
        reflectedMethod.returnType.asArgument().annotationMetadata
            .stringValue("test.TypeParameterMark").orElse(null) ==
            generatedMethod.returnType.asArgument().annotationMetadata
                .stringValue("test.TypeParameterMark").orElse(null)
    }

    void "metadata defaults stay scoped to the annotation class that supplied them"() {
        given: "two deployment class loaders define the same annotation name with different defaults"
        def firstLoader = buildClassLoader("duplicate.Subject", versionedSource("first"))
        def firstMetadata = ReflectionAnnotations.metadataOf(firstLoader.loadClass("duplicate.Subject"))

        expect:
        firstMetadata.getDefaultValue("duplicate.Versioned", "value", String).orElse(null) == "first"

        when: "reflection reads the annotation of the second deployment"
        def secondLoader = buildClassLoader("duplicate.Subject", versionedSource("second"))
        def secondMetadata = ReflectionAnnotations.metadataOf(secondLoader.loadClass("duplicate.Subject"))

        then:
        secondMetadata.getDefaultValue("duplicate.Versioned", "value", String).orElse(null) == "second"

        and: "the metadata already built for the first deployment keeps its own default"
        firstMetadata.getDefaultValue("duplicate.Versioned", "value", String).orElse(null) == "first"
    }

    private static String versionedSource(String defaultValue) {
        return """
package duplicate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Versioned
final class Subject {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Versioned {
    String value() default \"$defaultValue\";
}
"""
    }
}
