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

import io.micronaut.aop.Intercepted;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of FinalModifierSpec (Spock) to JUnit 5.
 * Uses AbstractTypeElementTest utilities for on-the-fly compilation.
 */
class FinalModifierTest extends AbstractTypeElementTest {

    @Test
    void testFinalModifierOnExternalClassProducedByFactory() {
        BeanContext context = buildContext("test.MyBeanFactory", """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;
            import tools.jackson.databind.ObjectMapper;

            @Factory
            class MyBeanFactory {
                @Mutating("someVal")
                @jakarta.inject.Singleton
                @jakarta.inject.Named("myMapper")
                ObjectMapper myMapper() {
                    return new ObjectMapper();
                }
            }
            """);
        try {
            Object bean = context.getBean(ObjectMapper.class, Qualifiers.byName("myMapper"));
            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, bean);
        } finally {
            context.close();
        }
    }

    @Test
    void testFinalModifierOnInheritedPublicMethod() {
        BeanDefinition<?> definition = buildBeanDefinition("test.CountryRepositoryImpl", """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            abstract class BaseRepositoryImpl {
                public final Object getContext() {
                    return new Object();
                }
            }

            interface CountryRepository {
            }

            @jakarta.inject.Singleton
            @Mutating("someVal")
            class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
                public String someMethod() {
                    return "test";
                }
            }
            """);
        assertNotNull(definition, "Compilation should pass");
    }

    @Test
    void testFinalModifierOnInheritedProtectedMethod() {
        BeanDefinition<?> definition = buildBeanDefinition("test.CountryRepositoryImpl", """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            abstract class BaseRepositoryImpl {
                protected final Object getContext() {
                    return new Object();
                }
            }

            interface CountryRepository {
            }

            @jakarta.inject.Singleton
            @Mutating("someVal")
            class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
                public String someMethod() {
                    return "test";
                }
            }
            """);
        assertNotNull(definition, "Compilation should pass");
    }

    @Test
    void testFinalModifierOnInheritedProtectedMethod2() {
        BeanDefinition<?> definition = buildBeanDefinition("test.CountryRepositoryImpl", """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            abstract class BaseRepositoryImpl {
                protected final Object getContext() {
                    return new Object();
                }
            }

            interface CountryRepository {
                @Mutating("someVal")
                public String someMethod();
            }

            @jakarta.inject.Singleton
            class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
                @Override
                public String someMethod() {
                    return "test";
                }
            }
            """);
        assertNotNull(definition, "Compilation should pass");
    }

    @Test
    void testFinalModifierOnFactoryWithAopAdviceDoesNotCompile() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition("test.MyBeanFactory", """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @Factory
            class MyBeanFactory {
                @Mutating("someVal")
                @jakarta.inject.Singleton
                MyBean myBean() {
                    return new MyBean();
                }
            }

            final class MyBean {
            }
            """));
        assertTrue(e.getMessage().contains("error: Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean"));
    }

    @Test
    void testFinalModifierOnClassWithAopAdviceDoesNotCompile() {
        String typeName = "test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX;
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition(typeName, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            final class MyBean {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                public String someMethod() {
                    return myValue;
                }
            }
            """));
        assertTrue(e.getMessage().contains("error: Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean"));
    }

    @Test
    void testFinalModifierOnMethodWithAopAdviceDoesNotCompile() {
        String typeName = "test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX;
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition(typeName, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            class MyBean {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                public final String someMethod() {
                    return myValue;
                }
            }
            """));
        assertTrue(e.getMessage().contains("error: Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class."));
    }

    @Test
    void testFinalModifierOnMethodWithAopAdviceOnMethodDoesNotCompile() {
        String typeName = "test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX;
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition(typeName, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @jakarta.inject.Singleton
            class MyBean {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                @Mutating("someVal")
                public final String someMethod() {
                    return myValue;
                }
            }
            """));
        assertTrue(e.getMessage().contains("error: Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied."));
    }
}
