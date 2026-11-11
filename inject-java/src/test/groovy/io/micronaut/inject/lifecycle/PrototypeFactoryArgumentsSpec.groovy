package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class PrototypeFactoryArgumentsSpec extends AbstractTypeElementSpec {

    void 'test a prototype factory is destroyed after its factory method that takes a prototype argument is called'() {
        given:
        ApplicationContext context = buildContext('''
package factory.argument;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    Dependent() {
        Events.LOG.add("Dependent created");
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent destroyed");
    }
}

class Product {
    final Dependent dependent;

    Product(Dependent dependent) {
        this.dependent = dependent;
    }
}

@Prototype
@Factory
class ProductFactory {
    ProductFactory() {
        Events.LOG.add("ProductFactory created");
    }

    @Prototype
    Product product(Dependent dependent) {
        Events.LOG.add("product called");
        return new Product(dependent);
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("ProductFactory destroyed");
    }
}
''')
        List<String> events = context.classLoader.loadClass('factory.argument.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('factory.argument.Product'))

        then:
        events == ['ProductFactory created', 'Dependent created', 'product called', 'ProductFactory destroyed']

        cleanup:
        context.close()
    }

    void 'test a prototype factory is destroyed after its factory method is called for a bean injected into a singleton'() {
        given:
        ApplicationContext context = buildContext('''
package factory.holder;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    Dependent() {
        Events.LOG.add("Dependent created");
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent destroyed");
    }
}

class Product {
    final Dependent dependent;

    Product(Dependent dependent) {
        this.dependent = dependent;
    }
}

@Prototype
@Factory
class ProductFactory {
    ProductFactory() {
        Events.LOG.add("ProductFactory created");
    }

    @Prototype
    Product product(Dependent dependent) {
        Events.LOG.add("product called");
        return new Product(dependent);
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("ProductFactory destroyed");
    }
}

@Singleton
class Holder {
    final Product product;

    Holder(Product product) {
        this.product = product;
    }
}
''')
        List<String> events = context.classLoader.loadClass('factory.holder.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('factory.holder.Holder'))

        then:
        events == ['ProductFactory created', 'Dependent created', 'product called', 'ProductFactory destroyed']

        when:
        context.close()

        then: 'the argument is destroyed with the product that holds it'
        events == ['ProductFactory created', 'Dependent created', 'product called', 'ProductFactory destroyed', 'Dependent destroyed']
    }

    void 'test a prototype factory is destroyed after its factory method that takes a prototype argument from another prototype factory is called'() {
        given:
        ApplicationContext context = buildContext('''
package factory.nested;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

class Dependent {
}

class Product {
    final Dependent dependent;

    Product(Dependent dependent) {
        this.dependent = dependent;
    }
}

@Prototype
@Factory
class DependentFactory {
    DependentFactory() {
        Events.LOG.add("DependentFactory created");
    }

    @Prototype
    Dependent dependent() {
        Events.LOG.add("dependent called");
        return new Dependent();
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("DependentFactory destroyed");
    }
}

@Prototype
@Factory
class ProductFactory {
    ProductFactory() {
        Events.LOG.add("ProductFactory created");
    }

    @Prototype
    Product product(Dependent dependent) {
        Events.LOG.add("product called");
        return new Product(dependent);
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("ProductFactory destroyed");
    }
}
''')
        List<String> events = context.classLoader.loadClass('factory.nested.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('factory.nested.Product'))

        then:
        events == [
            'ProductFactory created',
            'DependentFactory created',
            'dependent called',
            'DependentFactory destroyed',
            'product called',
            'ProductFactory destroyed'
        ]

        cleanup:
        context.close()
    }
}
