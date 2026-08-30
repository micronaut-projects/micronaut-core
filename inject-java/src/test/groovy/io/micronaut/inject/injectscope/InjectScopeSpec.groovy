package io.micronaut.inject.injectscope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InjectScopeSpec extends AbstractTypeElementSpec {

    void "test inject scope"() {
        given:
        def context = buildContext('''
package injectscopetest;

import io.micronaut.context.annotation.Bean;
import jakarta.annotation.PreDestroy;import jakarta.inject.*;
import io.micronaut.context.annotation.InjectScope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import java.util.*;

interface Connection extends AutoCloseable {
    @Override void close();
    
    boolean isOpen();
}

@Bean
class TestConnection implements Connection {
    public final Other other;
    TestConnection(Other o) {
        this.other = o;
    }
    public boolean isOpen = true;
    @Override public boolean isOpen() {
        return isOpen && other.isOpen;
    }
    @PreDestroy
    @Override public void close() {
        isOpen = false;    
    }
}

@Bean
class Other {
    boolean isOpen = true;
    @PreDestroy
    void close() {
        isOpen = false;
    }
}

@Singleton
class Test {
    public List<Connection> createdConnections = new ArrayList<injectscopetest.Connection>();
    
    Test(@InjectScope Connection conn1, @InjectScope Connection conn2) {
        Assertions.assertTrue(conn1.isOpen());
        Assertions.assertTrue(conn2.isOpen());
        createdConnections.add(conn1);
        createdConnections.add(conn2);
    }
    
    @Inject
    void init(@InjectScope Connection conn3) {
        Assertions.assertTrue(conn3.isOpen());
        createdConnections.add(conn3);
    }
}
''')
        def bean = getBean(context, 'injectscopetest.Test')

        expect:
        bean.createdConnections.size()
        bean.createdConnections.every { it.isOpen == false }
        bean.createdConnections.every { it.other.isOpen == false }

        cleanup:
        context.close()
    }

    void "test inject scope on a constructor parameter of a prototype bean destroys the bean and notifies listeners"() {
        given:
        def context = buildContext('''
package injectscopeprototype;

import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

@Prototype
class Connection {
    public boolean open = true;

    @PreDestroy
    void close() {
        open = false;
    }
}

@Prototype
class Resource {
    // no @PreDestroy of its own; destruction is only observable through the listener
}

@Singleton
class Holder {
    public final Connection connection;
    public final Resource resource;

    Holder(@InjectScope Connection connection, @InjectScope Resource resource) {
        this.connection = connection;
        this.resource = resource;
    }
}

@Singleton
class ResourceDestroyListener implements BeanPreDestroyEventListener<Resource> {
    public final AtomicInteger destroyed = new AtomicInteger();

    @Override
    public Resource onPreDestroy(BeanPreDestroyEvent<Resource> event) {
        destroyed.incrementAndGet();
        return event.getBean();
    }
}
''')

        when:
        def holder = getBean(context, 'injectscopeprototype.Holder')
        def listener = getBean(context, 'injectscopeprototype.ResourceDestroyListener')

        then: 'the prototype bean injected with @InjectScope is destroyed once the holder has been resolved'
        !holder.connection.open

        and: 'pre-destroy listeners run even for a bean with nothing else to dispose'
        listener.destroyed.get() == 1

        cleanup:
        context.close()
    }
}
