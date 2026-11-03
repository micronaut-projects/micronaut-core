package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.function.Supplier

class BeanElementBuilderTypeArgumentsForTypeSpec extends AbstractTypeElementSpec {

    void "test the type arguments given for a super type of a raw bean type are on the definition"() {
        given:
        def context = buildContext('rawsupertype.RawSupplier', '''
package rawsupertype;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class RawSupplier implements Supplier {
    @Override
    public Object get() {
        return "raw";
    }
}
''')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('rawsupertype.RawSupplier'))

        expect:
        definition.getTypeArguments(Supplier)*.type == [String]

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new RawSupertypeVisitor()]
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
