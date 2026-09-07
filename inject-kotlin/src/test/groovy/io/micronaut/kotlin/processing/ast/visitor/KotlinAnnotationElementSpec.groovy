package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

class KotlinAnnotationElementSpec extends AbstractKotlinCompilerSpec {

    void "test isInherited"() {
        given:
        InheritedVisitor.RESULTS.clear()
        buildClassElement("test.MyBean", """
package test

@java.lang.annotation.Inherited
annotation class MyInheritedAnn

annotation class MyPlainAnn

class MyBean
""")

        expect:
        InheritedVisitor.RESULTS['test.MyInheritedAnn']
        !InheritedVisitor.RESULTS['test.MyPlainAnn']
        InheritedVisitor.RESULTS[Introspected.name]
        !InheritedVisitor.RESULTS[Singleton.name]
    }

    static class InheritedVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Boolean> RESULTS = [:]

        @Override
        void start(VisitorContext visitorContext) {
            for (String name : ['test.MyInheritedAnn', 'test.MyPlainAnn', Introspected.name, Singleton.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    RESULTS.put(name, ((AnnotationElement) ce).isInherited())
                }
            }
        }
    }
}
