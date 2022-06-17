package io.micronaut.inject.dependent.factory

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.dependent.TestData
import spock.lang.Specification

class DestroyFactorySpec extends Specification {

    void "test destroy dependent objects from singleton using listeners"() {
        given:
            MyBean1Factory.beanCreated = 0
            MyBean1Factory.destroyed = 0
            MyBean2Factory.beanCreated = 0
            MyBean2Factory.destroyed = 0
            MyBean3Factory.beanDestroyed = 0
            MyBean3Factory.destroyed = 0
            TestData.DESTRUCTION_ORDER.clear()

        when:
            ApplicationContext context = ApplicationContext.run()
            BeanDefinition<MyBean1> beanDefinition = context.getBeanDefinition(MyBean1)
            BeanRegistration<MyBean1> registration = context.getBeanRegistration(beanDefinition)

        then:
            MyBean1Factory.destroyed == 1 // prototype
            MyBean1Factory.beanCreated == 1
            MyBean2Factory.destroyed == 1 // prototype
            MyBean2Factory.beanCreated == 1
            MyBean2Factory.beanDestroyed == 0
            MyBean3Factory.destroyed == 0 // singleton
            MyBean3Factory.beanDestroyed == 0

        when:
            context.destroyBean(registration)
        then:
            MyBean1Factory.destroyed == 1
            MyBean1Factory.beanCreated == 1
            MyBean2Factory.destroyed == 1
            MyBean2Factory.beanCreated == 1
            MyBean2Factory.beanDestroyed == 1  // prototype
            MyBean3Factory.destroyed == 0
            MyBean3Factory.beanDestroyed == 0 // singleton

        when:
            context.stop()

        then:
            MyBean1Factory.destroyed == 1
            MyBean1Factory.beanCreated == 1
            MyBean2Factory.destroyed == 1
            MyBean2Factory.beanCreated == 1
            MyBean2Factory.beanDestroyed == 1
            MyBean3Factory.destroyed == 1 // singleton
            MyBean3Factory.beanDestroyed == 1 // singleton

            TestData.DESTRUCTION_ORDER.size() == 5
            TestData.DESTRUCTION_ORDER.get(0) == 'MyBean1Factory'
            TestData.DESTRUCTION_ORDER.get(1) == 'MyBean2Factory'
            TestData.DESTRUCTION_ORDER.get(2) == 'MyBean2'

            TestData.DESTRUCTION_ORDER.count("MyBean3") == 1
            TestData.DESTRUCTION_ORDER.count("MyBean3Factory") == 1
    }
}
