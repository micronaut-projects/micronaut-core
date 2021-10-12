package io.micronaut.inject.factory.inheritance

import io.micronaut.aop.InterceptedProxy
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class FactoryAbstractInheritanceSpec extends AbstractBeanDefinitionSpec {
    void "test that beans are generated for child factories but not parent"() {
        given:
        def context = buildContext('''
package factinhertest;

import jakarta.inject.Singleton;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;


@Factory
class MyMockPublisherFactory extends MockPublisherFactory {
    
}

abstract class MockPublisherFactory {
    @Refreshable
    @Singleton
    @Replaces(PublisherFactory.class)
    Publisher createPublisher() {
        return new MockPublisher();
    }
}

class MockPublisher implements Publisher {}

interface Publisher {}
interface PublisherFactory {
    Publisher createPublisher();
}   
class ConcretePublisher implements Publisher {}

@Singleton
class DefaultPublisherFactory implements PublisherFactory {
    public Publisher createPublisher() {
        return new ConcretePublisher();
    }
}
''')
        def bean = getBean(context, 'factinhertest.Publisher')
        BeanDefinition definition = context.getBeanDefinition(
                context.classLoader.loadClass('factinhertest.Publisher')
        )

        expect:
        bean instanceof InterceptedProxy
        bean.interceptedTarget().getClass().simpleName == 'MockPublisher'
        definition.getClass().name.contains('MyMockPublisherFactory')
    }
}
