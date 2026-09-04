package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.BeanDestroyedEventListener
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

/**
 * A factory that is itself an event listener indexes its produced beans by the listener type too, even though
 * they do not implement it. Resolving the listeners must not try to instantiate one of those as a listener.
 */
class FactoryIndexLeakSpec extends AbstractTypeElementSpec {

    void "test a factory that is an event listener does not make its product a listener"() {
        given:
        ApplicationContext context = buildContext('leak.Product', '''
package leak;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

class Product {
}

@Factory
class ProductFactory implements BeanDestroyedEventListener<Product> {
    static boolean destroyed;

    @Singleton
    Product product() {
        return new Product();
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Product> event) {
        destroyed = true;
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('leak.Product')

        when: 'the product is created and the context is shut down, which resolves the destroyed listeners'
        context.getBean(productType)
        context.close()

        then: 'the product is not instantiated as a listener'
        noExceptionThrown()

        and: 'the factory did receive the event'
        context.classLoader.loadClass('leak.ProductFactory').destroyed
    }

    void "test the product is still enumerable by the index but is not a listener candidate"() {
        given:
        ApplicationContext context = buildContext('leak2.Product', '''
package leak2;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

class Product {
}

@Factory
class ProductFactory implements BeanDestroyedEventListener<Product> {
    @Singleton
    Product product() {
        return new Product();
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Product> event) {
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('leak2.Product')

        expect: 'the product carries the index inherited from its factory'
        context.getBeanDefinitions(BeanDestroyedEventListener)*.beanType.contains(productType)

        and: 'but it is not a candidate for the listener type, so it must never be resolved as one'
        !context.getBeanDefinitions(BeanDestroyedEventListener)
            .find { it.beanType == productType }
            .isCandidateBean(Argument.of(BeanDestroyedEventListener))

        cleanup:
        context.close()
    }
}
