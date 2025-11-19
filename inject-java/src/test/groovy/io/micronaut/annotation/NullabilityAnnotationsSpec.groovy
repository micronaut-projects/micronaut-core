package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.beans.BeanMethod
import io.micronaut.inject.ast.ElementQuery

class kNullabilityAnnotationsSpec extends AbstractTypeElementSpec {

    void "test map nullable annotation for #packageName in beans"() {
        given:
        def element = buildClassElement("""
package test;
import ${packageName}.*;
@jakarta.inject.Singleton
class Test {
    @Nullable
    String nullableMethod(@Nullable String test) {
        return null;
    }
}
""")
        def nullableMethod = element.getEnclosedElement(ElementQuery.ALL_METHODS.named({ String st -> st == 'nullableMethod' })).get()

        expect:
        nullableMethod.isNullable()
        !nullableMethod.isNonNull()
        nullableMethod.parameters[0].isNullable()
        !nullableMethod.parameters[0].isNonNull()

        where:
        packageName << ["io.micronaut.core.annotation", "javax.annotation", "org.jetbrains.annotations", "jakarta.annotation","edu.umd.cs.findbugs.annotations"]
    }

    void "test map nullable annotation for #packageName in introspections"() {
        given:
        def introspection = buildBeanIntrospection("test.Test", """
package test;

import ${packageName}.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected
class Test {
    @Nullable
    @Executable
    String nullableMethod(@Nullable String test) {
        return null;
    }
}
""")
        BeanMethod nullableMethod = introspection.getBeanMethods()[0]

        expect:
        nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE)
        !nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL)
        nullableMethod.arguments[0].isNullable()
        !nullableMethod.arguments[0].isNonNull()

        where:
        packageName << ["io.micronaut.core.annotation", "javax.annotation", "org.jetbrains.annotations", "jakarta.annotation","edu.umd.cs.findbugs.annotations"]
    }
}
