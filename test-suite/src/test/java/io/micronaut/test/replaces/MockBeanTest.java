/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
