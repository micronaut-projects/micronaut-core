package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.visitor.TypeElementVisitor

class BeanElementBuilderFactorySpec extends AbstractTypeElementSpec {

    void "test add associated factory bean"() {
        given:
        def context = buildContext('''
package factorybuilder;

import io.micronaut.context.annotation.Prototype;

@Prototype
class Foo {

}
''')
        expect:
        context.getBean(TestBeanProducer.BeanB) instanceof TestBeanProducer.BeanB
        context.getBean(TestBeanProducer.BeanA) instanceof TestBeanProducer.BeanA
        context.getBean(TestBeanProducer.BeanC) instanceof TestBeanProducer.BeanC
        context.getBean(TestBeanProducer.InterfaceA) instanceof TestBeanProducer.InterfaceA

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new TestBeanFactoryDefiningVisitor()]
    }
}
