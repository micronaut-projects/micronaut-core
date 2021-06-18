package io.micronaut.inject.dependent

import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.scope.CustomScope
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.Refreshable
import spock.lang.Specification

class DestroyDependentBeansSpec extends Specification {

    def cleanup() {
        TestData.DESTRUCTION_ORDER.clear()
    }
    void "test destroy dependent objects from singleton"() {
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
        TestData.DESTRUCTION_ORDER.remove("BeanE") // don't want to depend on collection order
        TestData.DESTRUCTION_ORDER.remove("BeanD") // don't want to depend on collection order
        TestData.DESTRUCTION_ORDER == ['SingletonBeanA', 'BeanB', 'BeanC', 'TestInterceptor', 'BeanB', 'BeanC', 'TestInterceptor', 'BeanB', 'BeanC', 'TestInterceptor']
    }

    void "test destroy dependent bean objects for custom scope"() {
        when:
        def context = ApplicationContext.run()
        def bean = context.getBean(ScopedBeanA)
        def refreshScope = context.getBean(CustomScope, Qualifiers.byExactTypeArgumentName(Refreshable.class.name))

        then:
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
        TestData.DESTRUCTION_ORDER == ['ScopedBeanA', 'BeanB', 'BeanC', 'TestInterceptor', 'BeanB', 'BeanC', 'TestInterceptor', 'BeanB', 'BeanC', 'TestInterceptor']
    }
}
