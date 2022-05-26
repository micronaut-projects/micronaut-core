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
}
