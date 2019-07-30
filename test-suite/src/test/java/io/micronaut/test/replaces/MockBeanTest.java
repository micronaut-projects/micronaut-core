package io.micronaut.test.replaces;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@MicronautTest
class MockBeanTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    InterfaceA interfaceA;

    @Inject
    InterfaceC interfaceC;

    @Inject
    InterfaceB interfaceB;


    @Test
    void testMockConcretePrimaryBean() {
        assertEquals("mocked A", interfaceA.doStuff());
    }

    @Test
    void testMockInterfaceImplPrimary() {
        assertEquals("mocked B", interfaceB.doStuff());
    }

    @Test
    void testMockInterfaceFromFactory() {
        assertEquals("mocked C", interfaceC.doStuff());
    }

    @MockBean(InterfaceAImpl.class)
    InterfaceA conreteBeanMock() {
        return () -> "mocked A";
    }

    @MockBean(InterfaceB.class)
    InterfaceB interfaceDefaultImplMock() {
        return () -> "mocked B";
    }


    @MockBean(InterfaceC.class)
    InterfaceC interfaceFactoryMock() {
        return () -> "mocked C";
    }
}
