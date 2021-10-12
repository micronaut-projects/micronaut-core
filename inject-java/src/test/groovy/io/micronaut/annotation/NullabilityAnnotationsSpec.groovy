package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ElementQuery

class NullabilityAnnotationsSpec extends AbstractTypeElementSpec {

    void "test map nullable annotation for #packageName"() {
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
        nullableMethod.parameters[0].isNullable()

        where:
        packageName << ["io.micronaut.core.annotation", "javax.annotation", "org.jetbrains.annotations", "edu.umd.cs.findbugs.annotations"]
    }
}
