package io.micronaut.inject.beanbuilder

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.AllElementsVisitor
import io.micronaut.inject.visitor.TypeElementVisitor

class BeanElementBuilderMultipleFactorySpec extends AbstractBeanDefinitionSpec {
    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, TestMultipleFactoryDefiningVisitor.name)
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
        context.getBean(OtherBeanProducer.BeanA).name == 'primary'
        context.getBean(OtherBeanProducer.BeanA, Qualifiers.byName("other")).name == 'other'

        cleanup:
        context.close()
    }

}
