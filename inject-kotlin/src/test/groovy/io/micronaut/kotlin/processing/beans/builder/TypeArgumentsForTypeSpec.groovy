package io.micronaut.kotlin.processing.beans.builder

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.function.Supplier

class TypeArgumentsForTypeSpec extends AbstractKotlinCompilerSpec {

    void "test the type arguments given for a super type replace those of the bean type"() {
        given:
        def context = buildContext('''
package krawsupertype

import java.util.function.Supplier

class RawSupplier : Supplier<Any> {
    override fun get(): Any = "raw"
}
''')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('krawsupertype.RawSupplier'))

        expect:
        definition.getTypeArguments(Supplier)*.type == [String]

        cleanup:
        context.close()
    }

    /**
     * Adds a bean of {@code krawsupertype.RawSupplier} that supplies {@code String}.
     */
    static class RawSupertypeVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'krawsupertype.RawSupplier') {
                element.addAssociatedBean(element)
                        .typeArgumentsForType(context.getClassElement(Supplier).get(), context.getClassElement(String).get())
            }
        }
    }
}
