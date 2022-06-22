package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor

class BeanElementBuilderMultipleFactorySpec extends AbstractTypeElementSpec {
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

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new TestMultipleFactoryDefiningVisitor()]
    }
}
