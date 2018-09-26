package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PropertyAnnotationSpec extends Specification {

    void "test inject properties"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'my.string':'foo',
                'my.int':10,
                'my.map.one':'one',
                'my.map.one.two':'two'
        )

        ConstructorPropertyInject constructorInjectedBean = ctx.getBean(ConstructorPropertyInject)
        MethodPropertyInject methodInjectedBean = ctx.getBean(MethodPropertyInject)
        FieldPropertyInject fieldInjectedBean = ctx.getBean(FieldPropertyInject)

        expect:
        constructorInjectedBean.nullable == null
        constructorInjectedBean.integer == 10
        constructorInjectedBean.str == 'foo'
        constructorInjectedBean.values == ['one':'one', 'one.two':'two']
        methodInjectedBean.nullable == null
        methodInjectedBean.integer == 10
        methodInjectedBean.str == 'foo'
        methodInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.nullable == null
        fieldInjectedBean.integer == 10
        fieldInjectedBean.str == 'foo'
        fieldInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.defaultInject == ['one':'one', 'one.two':'two']
    }
}
