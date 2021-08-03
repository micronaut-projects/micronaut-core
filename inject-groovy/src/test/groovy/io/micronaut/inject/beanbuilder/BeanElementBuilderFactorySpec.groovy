package io.micronaut.inject.beanbuilder

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.visitor.AllElementsVisitor

class BeanElementBuilderFactorySpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, TestBeanFactoryDefiningVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
        AllElementsVisitor.clearVisited()
    }

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

        cleanup:
        context.close()
    }

}
