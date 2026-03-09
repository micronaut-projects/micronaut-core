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
package io.micronaut.aop.introduction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of MyAbstractRepoSpec (Spock) to JUnit 5.
 */
class MyAbstractRepoTest extends AbstractTypeElementTest {

    @Test
    void testAbstractInterceptorMethod() throws Exception {
        try (ApplicationContext context = buildContext("test.MyAbstractRepo4", """
            package test;

            import io.micronaut.aop.introduction.Tx;
            import io.micronaut.aop.introduction.RepoDef;
            import io.micronaut.aop.introduction.DeleteByIdCrudRepo;
            import io.micronaut.context.annotation.Executable;

            @Tx
            @RepoDef
            abstract class MyAbstractRepo4 implements DeleteByIdCrudRepo<Integer> {

                public String findById(Integer id) {
                    return "ABC";
                }
            }
            """)) {
            ClassLoader cl = context.getClassLoader();
            Class<?> type = cl.loadClass("test.MyAbstractRepo4");
            BeanDefinition<?> beanDef = context.getBeanDefinition(type);
            assertNotNull(beanDef.getRequiredMethod("findById", Integer.class));
        }
    }

    @Test
    void testDefaultInterceptorMethod() throws Exception {
        try (ApplicationContext context = buildContext("test.MyDefaultRepo", """
            package test;

            import io.micronaut.aop.introduction.Tx;
            import io.micronaut.aop.introduction.RepoDef;
            import io.micronaut.aop.introduction.DeleteByIdCrudRepo;

            @Tx
            @RepoDef
            interface MyDefaultRepo extends DeleteByIdCrudRepo<Integer> {

                default String findById(Integer id) {
                    return "ABC";
                }
            }
            """)) {
            ClassLoader cl = context.getClassLoader();
            Class<?> type = cl.loadClass("test.MyDefaultRepo");
            BeanDefinition<?> beanDef = context.getBeanDefinition(type);
            assertNotNull(beanDef.getRequiredMethod("findById", Integer.class));
        }
    }
}
