package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class JvmNameOverloadMutationSpec extends AbstractKotlinCompilerSpec {

    void "an annotation a visitor adds to one of two overloads that differ by @JvmName is not seen on the other"() {
        when:
        def definition = buildBeanDefinition('jvmnamemutation.Overloads', '''
package jvmnamemutation

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
open class Overloads {
    @Executable
    fun handle(items: List<String>): String? = null

    @Executable
    @JvmName("handleInts")
    fun handle(items: List<Int>): String? = null
}
''')
        def methods = definition.executableMethods.collectEntries { [(it.methodName): it] }

        then:
        methods.keySet() == ['handle', 'handleInts'] as Set
        methods.handle.hasAnnotation(MyAnnotation)
        !methods.handleInts.hasAnnotation(MyAnnotation)
    }

    static class AnnotateStringOverloadVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.declaringType.name == 'jvmnamemutation.Overloads'
                    && element.parameters[0].genericType.firstTypeArgument.get().name == String.name) {
                element.annotate(MyAnnotation)
            }
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
        }
    }
}
