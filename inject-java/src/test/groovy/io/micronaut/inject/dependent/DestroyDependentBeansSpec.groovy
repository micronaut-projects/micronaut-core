package io.micronaut.inject.dependent

import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.scope.CustomScope
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.Refreshable
import spock.lang.Specification

class DestroyDependentBeansSpec extends Specification {

    void "test destroy dependent objects from singleton"() {
        given:
        TestData.DESTRUCTION_ORDER.clear()

        when:
        def context = ApplicationContext.run()
        def bean = context.getBean(SingletonBeanA)

        then:
        !bean.beanBField.destroyed
        !bean.beanBConstructor.destroyed
        !bean.beanBMethod.destroyed

        when:"When the context is stopped"
        context.stop()

        then:"Dependent objects stored"
        bean.destroyed
        bean.beanBField.destroyed
        bean.beanBConstructor.destroyed
        bean.beanBMethod.destroyed
        bean.beanBField.beanC.destroyed
        bean.beanBConstructor.beanC.destroyed
        bean.beanBMethod.beanC.destroyed
        bean.collection.every { it.destroyed }
        // don't want to depend on field/method order so have to do this
        TestData.DESTRUCTION_ORDER.first() == 'SingletonBeanA'
        TestData.DESTRUCTION_ORDER.count("BeanE") == 1
        TestData.DESTRUCTION_ORDER.count("BeanD") == 1
        TestData.DESTRUCTION_ORDER.count("BeanC") == 3
        TestData.DESTRUCTION_ORDER.count("BeanB") == 3
        TestData.DESTRUCTION_ORDER.count("TestInterceptor") == 3
    }

    void "test destroy dependent bean objects for custom scope"() {
        given:
        TestData.DESTRUCTION_ORDER.clear()
        
        when:
        def context = ApplicationContext.run()
        def bean = context.getBean(ScopedBeanA)
        def refreshScope = context.getBean(CustomScope, Qualifiers.byExactTypeArgumentName(Refreshable.class.name))

        then:
        TestData.DESTRUCTION_ORDER.isEmpty()
        refreshScope.findBeanRegistration(bean).isPresent()
        !bean.beanBField.destroyed
        !bean.beanBConstructor.destroyed
        !bean.beanBMethod.destroyed
        bean instanceof InterceptedProxy


        when:"When the context is stopped"
        def target = bean.interceptedTarget()
        context.stop()

        then:"Dependent objects stored"
        target.destroyed
        target.beanBField.destroyed
        target.beanBConstructor.destroyed
        target.beanBMethod.destroyed
        target.beanBField.beanC.destroyed
        target.beanBConstructor.beanC.destroyed
        target.beanBMethod.beanC.destroyed
        TestData.DESTRUCTION_ORDER.first() == 'ScopedBeanA'
        TestData.DESTRUCTION_ORDER.count("BeanC") == 3
        TestData.DESTRUCTION_ORDER.count("BeanB") == 3
        TestData.DESTRUCTION_ORDER.count("TestInterceptor") == 3
    }
}
