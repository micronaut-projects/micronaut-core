package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.beans.BeanMethod
import io.micronaut.inject.ast.ElementQuery

class NonNullabilityAnnotationsSpec extends AbstractTypeElementSpec {

    void "test map nonnull annotation for #packageName in beans"() {
        given:
        def element = buildClassElement("""
package test;
import ${packageName}.*;
@jakarta.inject.Singleton
class Test {
    ${annotation}
    String notNullableMethod(${annotation} String test) {
        return "";
    }
}
""")
        def nullableMethod = element.getEnclosedElement(ElementQuery.ALL_METHODS.named({ String st -> st == 'notNullableMethod' })).get()

        expect:
        !nullableMethod.isNullable()
        nullableMethod.isNonNull()
        !nullableMethod.parameters[0].isNullable()
        nullableMethod.parameters[0].isNonNull()

        where:
        packageName                       | annotation
        "io.micronaut.core.annotation"    | "@NonNull"
        "edu.umd.cs.findbugs.annotations" | "@NonNull"
        "javax.annotation"                | "@Nonnull"
        "jakarta.annotation"              | "@Nonnull"
        "org.jetbrains.annotations"       | "@NotNull"
    }

    void "test map nonnull annotation for #packageName in introspections"() {
        given:
        def introspection = buildBeanIntrospection("test.Test", """
package test;

import ${packageName}.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected
class Test {
    ${annotation}
    @Executable
    String notNullableMethod(${annotation} String test) {
        return "";
    }
}
""")
        BeanMethod nullableMethod = introspection.getBeanMethods()[0]

        expect:
        !nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE)
        nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL)
        !nullableMethod.arguments[0].isNullable()
        nullableMethod.arguments[0].isNonNull()

        where:
        packageName                       | annotation
        "io.micronaut.core.annotation"    | "@NonNull"
        "edu.umd.cs.findbugs.annotations" | "@NonNull"
        "javax.annotation"                | "@Nonnull"
        "jakarta.annotation"              | "@Nonnull"
        "org.jetbrains.annotations"       | "@NotNull"
    }
}
