/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.compile;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.StaticOriginatingElements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of OriginatingElementsSpec.
 */
class OriginatingElementsTest extends AbstractTypeElementTest {

    private static void assertOriginatingElements(String... names) {
        var origin = StaticOriginatingElements.INSTANCE.getOriginatingElements();
        assertEquals(names.length, origin.length);
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], origin[i].getName());
        }
    }

    @AfterEach
    void cleanup() {
        StaticOriginatingElements.INSTANCE.clear();
        System.clearProperty("micronaut.static.originating.elements");
    }

    @Test
    void testInjectAnnotationInheritedThroughAbstractBase() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import jakarta.inject.*;

            @Singleton
            class MyBean extends MyBase {
            }

            abstract class MyBase {
                @Inject
                io.micronaut.core.convert.ConversionService conversionService;
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(1, beanDefinition.getInjectedFields().size());

        // the originating elements include the super class
        assertOriginatingElements("test.MyBean", "test.MyBase");
    }

    @Test
    void testBaseClassNotIncludedIfNoInjectionPoints() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import jakarta.inject.*;

            @Singleton
            class MyBean extends MyBase {
            }

            abstract class MyBase {
                io.micronaut.core.convert.ConversionService conversionService;
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        assertOriginatingElements("test.MyBean");
    }

    @Test
    void testExecutableMethodInheritedThroughAbstractBase() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import jakarta.inject.*;

            @Singleton
            class MyBean extends MyBase {
            }

            abstract class MyBase {
                @Executable
                void myMethod() {
                    // no-op
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1, beanDefinition.getExecutableMethods().size());

        assertOriginatingElements("test.MyBean", "test.MyBase");
    }

    @Test
    void testAopMethodInheritedThroughAbstractBase() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import io.micronaut.aop.simple.*;
            import jakarta.inject.*;

            @Singleton
            class MyBean extends MyBase {
            }

            abstract class MyBase {
                @Mutating("name")
                void myMethod(String name) {
                    // no-op
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1, beanDefinition.getExecutableMethods().size());

        assertOriginatingElements("test.MyBean", "test.MyBase");
    }

    @Test
    void testInjectAnnotationOnMethodInheritedThroughAbstractBase() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import jakarta.inject.*;
            import io.micronaut.core.convert.*;

            @Singleton
            class MyBean extends MyBase {
            }

            abstract class MyBase {

                private ConversionService conversionService;

                @Inject
                void setConversionService(ConversionService conversionService) {
                    this.conversionService = conversionService;
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(1, beanDefinition.getInjectedMethods().size());

        assertOriginatingElements("test.MyBean", "test.MyBase");
    }

    @Test
    void testOriginatingElementsFromIntroductionAdviseInterfaceInheritance() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;
            import jakarta.validation.constraints.*;

            interface MyInterface{
                @Executable
                void save(@NotBlank String name, @Min(1L) int age);
                @Executable
                void saveTwo(@Min(1L) String name);
            }

            @Stub
            @jakarta.inject.Singleton
            interface MyBean extends MyInterface {
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertOriginatingElements("test.MyBean", "test.MyInterface");
        assertEquals(2, beanDefinition.getExecutableMethods().size());
    }

    @Test
    void testOriginatingElementsFromAbstractIntroductionAdviseInterfaceInheritance() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;
            import jakarta.validation.constraints.*;

            interface MyInterface {
                @Executable
                void save(@NotBlank String name, @Min(1L) int age);
                @Executable
                void saveTwo(@Min(1L) String name);
            }

            @Stub
            @jakarta.inject.Singleton
            abstract class MyBean implements MyInterface {
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertOriginatingElements("test.MyBean", "test.MyInterface");
    }

    @Test
    void testOriginatingElementsFromAbstractExtendedIntroductionAdviseInterfaceInheritance() {
        System.setProperty("micronaut.static.originating.elements", "true");

        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;
            import jakarta.validation.constraints.*;

            interface MyInterface {
                @Executable
                void save(@NotBlank String name, @Min(1L) int age);
                @Executable
                void saveTwo(@Min(1L) String name);
            }

            @Stub
            @jakarta.inject.Singleton
            abstract class MyBean extends MyParentBean {
            }

            abstract class MyParentBean implements MyInterface {
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        // MyParentBean is not included because it has no bean related annotations
        assertOriginatingElements("test.MyBean", "test.MyInterface");
    }
}
