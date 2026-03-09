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

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of ExecutableFactoryMethodSpec.
 */
class ExecutableFactoryMethodTest extends AbstractTypeElementTest {

    @Test
    void testExecutingDefaultInterfaceMethod() throws Exception {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyFactory$MyClass0", """
            package test;

            import io.micronaut.context.annotation.*;
            import jakarta.inject.*;

            interface SomeInterface {

                String goDog();
                default String go() { return "go"; }
            }

            @Factory
            class MyFactory {

                @Singleton
                @Executable
                MyClass myClass() {
                    return new MyClass();
                }
            }

            class MyClass implements SomeInterface {

                @Override
                public String goDog() {
                    return "go";
                }
            }
            """);

        assertNotNull(beanDefinition);

        Object instance = ReflectionUtils.instantiate(
            beanDefinition.getClass().getClassLoader().loadClass("test.MyClass")
        );

        var goMethod = (ExecutableMethod<Object, Object>) beanDefinition.getRequiredMethod("go");
        var goDogMethod = (ExecutableMethod<Object, Object>) beanDefinition.getRequiredMethod("goDog");
        assertEquals("go", goMethod.invoke(instance));
        assertEquals("go", goDogMethod.invoke(instance));
    }

    @Test
    void testExecutableFactoryWithMultipleInterfaceInheritance() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyFactory$MyClient0", """
            package test;

            import reactor.core.publisher.Flux;
            import io.micronaut.context.annotation.*;
            import jakarta.inject.*;
            import org.reactivestreams.Publisher;

            @Factory
            class MyFactory {

                @Singleton
                @Executable
                MyClient myClient() {
                    return null;
                }
            }

            interface HttpClient {
                Publisher retrieve();
            }
            interface StreamingHttpClient extends HttpClient {
                Publisher<byte[]> stream();
            }
            interface ReactorHttpClient extends HttpClient {
                @Override
                Flux retrieve();
            }
            interface ReactorStreamingHttpClient extends StreamingHttpClient, ReactorHttpClient {
                @Override
                Flux<byte[]> stream();
            }
            interface MyClient extends ReactorStreamingHttpClient {
                byte[] blocking();
            }
            """);

        assertNotNull(beanDefinition);
        var retrieveMethod = beanDefinition.getRequiredMethod("retrieve");
        var blockingMethod = beanDefinition.getRequiredMethod("blocking");
        var streamMethod = beanDefinition.getRequiredMethod("stream");

        assertEquals(Flux.class, retrieveMethod.getReturnType().getType());
        assertEquals(Flux.class, streamMethod.getReturnType().getType());

        assertEquals(1, retrieveMethod.getReturnType().getTypeParameters().length);
        assertEquals(Object.class, retrieveMethod.getReturnType().getTypeParameters()[0].getType());

        assertEquals(1, streamMethod.getReturnType().getTypeParameters().length);
        assertEquals(byte[].class, streamMethod.getReturnType().getTypeParameters()[0].getType());

        assertEquals(byte[].class, blockingMethod.getReturnType().getType());
    }
}
