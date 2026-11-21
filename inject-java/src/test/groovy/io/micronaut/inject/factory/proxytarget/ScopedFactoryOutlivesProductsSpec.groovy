package io.micronaut.inject.factory.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.BeanInstantiationException

/**
 * A factory in a scope with a scoped proxy is resolved for each product as its proxy. Once a product has been
 * created, or its creation has failed, the proxy is released as a dependent is, which leaves the factory instance in
 * its scope: the instance produces every product and is destroyed with its scope, not after each product.
 */
class ScopedFactoryOutlivesProductsSpec extends AbstractTypeElementSpec {

    private static final String IMPORTS = '''
package scopedfactory;

import io.micronaut.context.annotation.*;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
'''

    private static final String SCOPE = '''
class Log {
    static final List<String> events = new CopyOnWriteArrayList<>();
    static final AtomicInteger created = new AtomicInteger();
}

@ScopedProxy
@Scope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Conversation {
}

@Singleton
class ConversationScope extends AbstractConcurrentCustomScope<Conversation> {
    private final Map<BeanIdentifier, CreatedBean<?>> conversation = new ConcurrentHashMap<>();

    ConversationScope() {
        super(Conversation.class);
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return conversation;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void close() {
        destroyScope(conversation);
    }
}

class Product {
    final int factory;
    Product(int factory) { this.factory = factory; }
}
'''

    void "the instance of a scoped factory outlives the products it creates"() {
        given:
        def context = buildContext(IMPORTS + SCOPE + '''
@Factory
@Conversation
class Products {
    private final int id = Log.created.incrementAndGet();

    @Prototype
    Product product() {
        return new Product(id);
    }

    @PreDestroy
    void destroy() { Log.events.add("factory " + id + " destroyed"); }
}
''')
        def log = context.classLoader.loadClass('scopedfactory.Log')
        def productType = context.classLoader.loadClass('scopedfactory.Product')

        when:
        def first = context.getBean(productType)
        def second = context.getBean(productType)
        def registration = context.createDependencyGroup().createBeanRegistration(context.getBeanDefinition(productType))
        context.destroyBean(registration)
        def third = context.getBean(productType)

        then: "one factory instance produced every product and none was destroyed"
        // a scoped proxy extends the factory class and takes an id as it is constructed, the first one before the
        // factory instance in the scope does
        [first, second, third, registration.bean()]*.factory == [2, 2, 2, 2]
        log.events.empty

        when:
        context.close()

        then: "the factory instance is destroyed with its scope"
        log.events == ['factory 2 destroyed']
    }

    void "the instance of a scoped factory outlives a product whose creation fails"() {
        given:
        def context = buildContext(IMPORTS + SCOPE + '''
@Factory
@Conversation
class Products {
    static boolean failed;
    private final int id = Log.created.incrementAndGet();

    @Prototype
    Product product() {
        if (!failed) {
            failed = true;
            throw new IllegalStateException("the first product fails");
        }
        return new Product(id);
    }

    @PreDestroy
    void destroy() { Log.events.add("factory " + id + " destroyed"); }
}
''')
        def log = context.classLoader.loadClass('scopedfactory.Log')
        def productType = context.classLoader.loadClass('scopedfactory.Product')

        when:
        context.getBean(productType)

        then:
        thrown(BeanInstantiationException)
        log.events.empty

        when:
        def product = context.getBean(productType)

        then: "the factory instance the failed creation used produces the next product and none was destroyed"
        product.factory == 2
        log.events.empty

        when:
        context.close()

        then: "the factory instance is destroyed with its scope"
        log.events == ['factory 2 destroyed']
    }
}
