package io.micronaut.inject.dependent

import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.scope.CustomScope
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.dependent.listeners.AnotherSingletonBeanA
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.Refreshable
import spock.lang.Specification

class DestroyDependentBeansSpec extends Specification {

    void setup() {
        TestData.DESTRUCTION_ORDER.clear()
    }

    void "test destroy dependent objects from singleton"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        SingletonBeanA bean = context.getBean(SingletonBeanA)

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

        cleanup:
        TestData.DESTRUCTION_ORDER.clear()
    }

    void "test destroy dependent bean objects for custom scope"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        ScopedBeanA bean = context.getBean(ScopedBeanA)
        CustomScope refreshScope = context.getBean(CustomScope, Qualifiers.byExactTypeArgumentName(Refreshable.class.name))

        then:
        TestData.DESTRUCTION_ORDER.isEmpty()
        refreshScope.findBeanRegistration(bean).isPresent()
        !bean.beanBField.destroyed
        !bean.beanBConstructor.destroyed
        !bean.beanBMethod.destroyed
        bean instanceof InterceptedProxy

        when:"When the context is stopped"
        ScopedBeanA target = ((InterceptedProxy<ScopedBeanA>) bean).interceptedTarget()
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

        cleanup:
        TestData.DESTRUCTION_ORDER.clear()
    }

    void "test destroy dependent objects from singleton bean without callback"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            SingletonBeanANoCallback bean = context.getBean(SingletonBeanANoCallback)

        then:
            !bean.beanBField.destroyed
            !bean.beanBConstructor.destroyed
            !bean.beanBMethod.destroyed

        when:
            BeanRegistration<SingletonBeanANoCallback> registration = context.findBeanRegistration(bean).orElse(null)
        then:
            registration
        when:
            List<BeanRegistration> dependents = registration.dependents
        then: "Validate dependents"
            dependents.size() == 5
            dependents[0].bean instanceof BeanB

        when:

            List<BeanRegistration> beanBDependents = dependents[0].dependents
        then:"Validate BeanB dependents"
            beanBDependents.size() == 2
            beanBDependents[0].bean instanceof TestInterceptor
            beanBDependents[0].dependents == null
            beanBDependents[1].bean instanceof BeanC
            beanBDependents[1].dependents == null

        when:"When the context is stopped"
            context.stop()

        then:"Dependent objects stored"
            bean.beanBField.destroyed
            bean.beanBConstructor.destroyed
            bean.beanBMethod.destroyed
            bean.beanBField.beanC.destroyed
            bean.beanBConstructor.beanC.destroyed
            bean.beanBMethod.beanC.destroyed
            bean.collection.every { it.destroyed }
            // don't want to depend on field/method order so have to do this
            TestData.DESTRUCTION_ORDER.count("BeanE") == 1
            TestData.DESTRUCTION_ORDER.count("BeanD") == 1
            TestData.DESTRUCTION_ORDER.count("BeanC") == 3
            TestData.DESTRUCTION_ORDER.count("BeanB") == 3
            TestData.DESTRUCTION_ORDER.count("TestInterceptor") == 3

        cleanup:
            TestData.DESTRUCTION_ORDER.clear()
    }

    void "test destroy dependent objects from prototype bean without callback"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            BeanDefinition<PrototypeBeanA> beanDefinition = context.getBeanDefinition(PrototypeBeanA)
            BeanRegistration<PrototypeBeanA> registration = context.getBeanRegistration(beanDefinition)
            PrototypeBeanA bean = registration.bean

        then:
            !bean.beanBField.destroyed
            !bean.beanBConstructor.destroyed
            !bean.beanBMethod.destroyed

        when:
            List<BeanRegistration> dependents = registration.dependents
        then: "Validate dependents"
            dependents.size() == 5
            dependents[0].bean instanceof BeanB

        when:
            List<BeanRegistration> beanBDependents = dependents[0].dependents
        then:"Validate BeanB dependents"
            beanBDependents.size() == 2
            beanBDependents[0].bean instanceof TestInterceptor
            beanBDependents[0].dependents == null
            beanBDependents[1].bean instanceof BeanC
            beanBDependents[1].dependents == null

        when:"When the context is stopped"
            context.destroyBean(registration)

        then:"Dependent objects stored"
            bean.beanBField.destroyed
            bean.beanBConstructor.destroyed
            bean.beanBMethod.destroyed
            bean.beanBField.beanC.destroyed
            bean.beanBConstructor.beanC.destroyed
            bean.beanBMethod.beanC.destroyed
            bean.collection.every { it.destroyed }
            // don't want to depend on field/method order so have to do this
            TestData.DESTRUCTION_ORDER.count("BeanE") == 1
            TestData.DESTRUCTION_ORDER.count("BeanD") == 1
            TestData.DESTRUCTION_ORDER.count("BeanC") == 3
            TestData.DESTRUCTION_ORDER.count("BeanB") == 3
            TestData.DESTRUCTION_ORDER.count("TestInterceptor") == 3
        cleanup:
            TestData.DESTRUCTION_ORDER.clear()
    }

    void "test destroy dependent objects from singleton using listeners"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            AnotherSingletonBeanA bean = context.getBean(AnotherSingletonBeanA)

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
            TestData.DESTRUCTION_ORDER.first() == 'AnotherSingletonBeanA'
            TestData.DESTRUCTION_ORDER.count("AnotherBeanE") == 1
            TestData.DESTRUCTION_ORDER.count("AnotherBeanD") == 1
            TestData.DESTRUCTION_ORDER.count("AnotherBeanC") == 3
            TestData.DESTRUCTION_ORDER.count("AnotherBeanB") == 3
            TestData.DESTRUCTION_ORDER.count("TestInterceptor") == 3
        cleanup:
            TestData.DESTRUCTION_ORDER.clear()
    }
}
