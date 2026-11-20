package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.visitor.producebeans.ProduceBeansVisitor
import io.micronaut.python.processing.element.PythonClassElement

/**
 * The beans {@link ProduceBeansVisitor} produces from a Python class: the stub bridges each producer method, a
 * primitive product included, and a method returning nothing produces none.
 */
class ProduceBeansAccessSpec extends AbstractPythonTypeElementSpec {

    void "test produceBeans produces a primitive from a Python method and skips one returning nothing"() {
        given:
        def context = buildContext('''
class Product:

    def __init__(self, name: str):
        self.name = name

    def describe(self) -> str:
        return self.name

class AccessProducers:

    def produce_product(self) -> Product:
        return Product("product")

    def produce_number(self) -> int:
        return 42

    def produce_nothing(self) -> None:
        pass
''')
        def number = context.allBeanDefinitions.find { it.stringValue(AnnotationUtil.NAMED).orElse(null) == 'produce_number' }

        expect:
        context.getBean(context.classLoader.loadClass('python.Product'), Qualifiers.byName('produce_product'))
            .asPolyglotValue().invokeMember('describe').asString() == 'product'
        number.beanType.isPrimitive()
        context.getBean(number.beanType, Qualifiers.byName('produce_number')) == 42
        !context.allBeanDefinitions.any { it.stringValue(AnnotationUtil.NAMED).orElse(null) == 'produce_nothing' }

        cleanup:
        context?.close()
    }

    /**
     * Produces from the Python class only: javac visits the Java stub of the class as well.
     */
    static class PythonProduceBeansVisitor extends ProduceBeansVisitor {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element instanceof PythonClassElement) {
                super.visitClass(element, context)
            }
        }
    }
}
