package io.micronaut.inject.beanbuilder

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.function.Supplier

class BeanElementBuilderTypeArgumentsForTypeSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, RawSupertypeVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test the type arguments given for a super type of a raw bean type are on the definition"() {
        given:
        def context = buildContext('''
package rawsupertype

import java.util.function.Supplier

class RawSupplier implements Supplier {
    @Override
    Object get() {
        return "raw"
    }
}
''')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('rawsupertype.RawSupplier'))

        expect:
        definition.getTypeArguments(Supplier)*.type == [String]

        cleanup:
        context.close()
    }

    /**
     * Adds a bean of {@code rawsupertype.RawSupplier} that supplies {@code String}.
     */
    static class RawSupertypeVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'rawsupertype.RawSupplier') {
                element.addAssociatedBean(element)
                        .typeArgumentsForType(context.getClassElement(Supplier).get(), context.getClassElement(String).get())
            }
        }
    }
}
