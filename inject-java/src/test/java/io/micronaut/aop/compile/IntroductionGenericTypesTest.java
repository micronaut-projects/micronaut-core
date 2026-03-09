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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of IntroductionGenericTypesSpec.
 */
class IntroductionGenericTypesTest extends AbstractTypeElementTest {

    private static ReturnType<?> returnType(BeanDefinition<?> bd, String name) {
        return bd.findPossibleMethods(name).findFirst().orElseThrow().getReturnType();
    }

    @Test
    void testGenericReturnTypesWhenImplementingInterfaceWithTypeArguments() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;

            interface MyInterface<T extends URL> {

                T getURL();

                java.util.List<T> getURLs();
            }

            @Stub
            @jakarta.inject.Singleton
            @Executable
            interface MyBean extends MyInterface<URL> {
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(2, beanDefinition.getExecutableMethods().size());

        ExecutableMethod<?, ?> getUrlMethod = beanDefinition.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("getURL"))
            .findFirst().orElseThrow();
        assertEquals("getURL", getUrlMethod.getMethodName());
        assertNotNull(getUrlMethod.getTargetMethod());
        assertEquals(URL.class, getUrlMethod.getReturnType().getType());

        ExecutableMethod<?, ?> getUrlsMethod = beanDefinition.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("getURLs"))
            .findFirst().orElseThrow();
        assertEquals(List.class, getUrlsMethod.getReturnType().getType());
        assertEquals(1, getUrlsMethod.getReturnType().getTypeParameters().length);
        assertEquals(URL.class, getUrlsMethod.getReturnType().getTypeParameters()[0].getType());
    }

    @Test
    void testGenericReturnTypesWhenImplementingInterfaceWithTypeArguments2() throws Exception {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;

            interface MyInterface<T extends Person> {

                reactor.core.publisher.Mono<java.util.List<T>> getPeopleSingle();

                T getPerson();

                java.util.List<T> getPeople();

                void save(T person);

                void saveAll(java.util.List<T> person);

                T[] getPeopleArray();

                java.util.List<T[]> getPeopleListArray();

                <V extends java.net.URL> java.util.Map<T,V> getPeopleMap();
            }

            @Stub
            @jakarta.inject.Singleton
            @Executable
            interface MyBean extends MyInterface<SubPerson> {
            }

            class Person {}
            class SubPerson extends Person {}
            """);

        assertNotNull(beanDefinition);
        // Return type of getPerson: SubPerson
        assertEquals("test.SubPerson", returnType(beanDefinition, "getPerson").getType().getName());

        // Return type of getPeople: List<SubPerson>
        assertEquals(List.class, returnType(beanDefinition, "getPeople").getType());
        assertEquals(1, returnType(beanDefinition, "getPeople").getTypeParameters().length);
        assertEquals("test.SubPerson", returnType(beanDefinition, "getPeople").getTypeParameters()[0].getType().getName());

        // Return type of getPeopleMap: Map<SubPerson, URL>
        var peopleMap = returnType(beanDefinition, "getPeopleMap");
        assertEquals(2, peopleMap.getTypeParameters().length);
        assertEquals("test.SubPerson", peopleMap.getTypeParameters()[0].getType().getName());
        assertEquals(URL.class, peopleMap.getTypeParameters()[1].getType());

        // Return type of getPeopleArray: SubPerson[]
        var peopleArray = returnType(beanDefinition, "getPeopleArray");
        assertTrue(peopleArray.getType().isArray());
        assertTrue(peopleArray.getType().getComponentType().getName().contains("test.SubPerson"));

        // Return type of getPeopleListArray: List<T[]>
        var peopleListArray = returnType(beanDefinition, "getPeopleListArray");
        assertEquals(List.class, peopleListArray.getType());
        assertEquals(1, peopleListArray.getTypeParameters().length);
        assertTrue(peopleListArray.getTypeParameters()[0].getType().isArray());

        // Ensure target methods are resolved (non-reflective dispatch)
        assertNotNull(beanDefinition.findPossibleMethods("save").findFirst().orElseThrow().getTargetMethod());
        assertNotNull(beanDefinition.findPossibleMethods("getPerson").findFirst().orElseThrow().getTargetMethod());

        // getPeopleSingle: Mono<List<SubPerson>>; assert nested generics
        var peopleSingle = returnType(beanDefinition, "getPeopleSingle");
        assertEquals("reactor.core.publisher.Mono", peopleSingle.getType().getName());
        assertEquals(1, peopleSingle.getTypeParameters().length);
        var innerListArg = peopleSingle.getTypeParameters()[0];
        assertEquals(List.class, innerListArg.getType());
        assertEquals(1, innerListArg.getTypeParameters().length);
        assertEquals("test.SubPerson", innerListArg.getTypeParameters()[0].getType().getName());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            // methods are invocable and return null (stubs)
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, "getPerson"));
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, "getPeople"));
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, "getPeopleArray"));
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, "getPeopleSingle"));
            java.lang.reflect.Method mSave = java.util.Arrays.stream(instance.getClass().getDeclaredMethods())
                .filter(mm -> mm.getName().equals("save") && mm.getParameterCount() == 1)
                .findFirst().orElseThrow();
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, mSave, (Object) null));
            java.lang.reflect.Method mSaveAll = java.util.Arrays.stream(instance.getClass().getDeclaredMethods())
                .filter(mm -> mm.getName().equals("saveAll") && mm.getParameterCount() == 1)
                .findFirst().orElseThrow();
            assertNull(ReflectionUtils.invokeInaccessibleMethod(instance, mSaveAll, List.of()));
        }
    }
}
