/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.context

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.reload.ClassChange
import io.micronaut.context.reload.ClassChangeEvent
import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DelegatingBeanDefinition
import io.micronaut.inject.ProxyBeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared

class ClassChangeEventDefinitionSpec extends AbstractTypeElementSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context

    def setupSpec() {
        context = buildContext('test.Plain', '''
package test;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Singleton
class Plain {
}

@Singleton
class PlainExtra {
}

interface Greeting {
    default String greet() {
        return "hello";
    }
}

interface PoliteGreeting extends Greeting {
}

abstract class Base implements PoliteGreeting {
}

@Singleton
class Child extends Base {
}

class Outer {
    @Singleton
    static class Inner {
    }
}

class Product {
}

@Factory
class Products {
    @Singleton
    Product product() {
        return new Product();
    }
}

@Around(proxyTarget = true)
@Retention(RetentionPolicy.RUNTIME)
@interface Traced {
}

@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Traced
@Singleton
class Service {
    public String hello() {
        return "hello";
    }
}

@Introduction
@Retention(RetentionPolicy.RUNTIME)
@interface Stub {
}

@InterceptorBean(Stub.class)
class StubInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "stub";
    }
}

@Stub
interface Client {
    String hello();
}

@EachProperty("things")
class Thing {
}
''', false, ['things.first.enabled': true])
    }

    void "a definition is affected by a change of its bean type or a supertype of it, not of a class sharing its name prefix"() {
        expect:
        event('test.Plain').affectsDefinition(definition('test.Plain'))
        !event('test.Plain').affectsDefinition(definition('test.PlainExtra'))
        !event('test.PlainExtra').affectsDefinition(definition('test.Plain'))
        event('test.Base').affectsDefinition(definition('test.Child'))
        event('test.PoliteGreeting').affectsDefinition(definition('test.Child'))
        event('test.Greeting').affectsDefinition(definition('test.Child'))
        !event('test.Greeting').affectsDefinition(definition('test.Plain'))
        !event('test.Child').affectsDefinition(definition('test.Plain'))
    }

    void "a nested class is a class of its own"() {
        expect:
        event('test.Outer$Inner').affectsDefinition(definition('test.Outer$Inner'))
        !event('test.Outer').affectsDefinition(definition('test.Outer$Inner'))
    }

    void "a factory product is affected by a change of the factory or of the product"() {
        given:
        BeanDefinition<?> product = definition('test.Product')

        expect:
        product.declaringType.get().name == 'test.Products'
        event('test.Products').affectsDefinition(product)
        event('test.Product').affectsDefinition(product)
        !event('test.Plain').affectsDefinition(product)
    }

    void "the definitions of around advice are affected by a change of the advised class"() {
        given:
        Class<?> service = type('test.Service')
        BeanDefinition<?> proxy = context.getBeanDefinition(service)
        BeanDefinition<?> target = context.getProxyTargetBeanDefinition(service, null)

        expect:
        proxy instanceof ProxyBeanDefinition
        !target.is(proxy)
        event('test.Service').affectsDefinition(proxy)
        event('test.Service').affectsDefinition(target)
        !event('test.Plain').affectsDefinition(proxy)
    }

    void "the definition of introduction advice is affected by a change of the introduced interface"() {
        given:
        BeanDefinition<?> client = definition('test.Client')

        expect:
        event('test.Client').affectsDefinition(client)
        !event('test.Plain').affectsDefinition(client)
    }

    void "a definition delivered per entry is affected through the definition it delegates to"() {
        given:
        Collection<BeanDefinition<?>> things = context.getBeanDefinitions(type('test.Thing'))

        expect:
        things.size() == 1
        things.every { it instanceof DelegatingBeanDefinition }
        things.every { event('test.Thing').affectsDefinition(it) }
        things.every { !event('test.Plain').affectsDefinition(it) }
    }

    void "a definition is affected by a change of the definition class itself"() {
        given:
        BeanDefinition<?> plain = definition('test.Plain')

        expect:
        event(plain.getClass().name).affectsDefinition(plain)
    }

    void "nothing is affected by no change, nor is a missing definition"() {
        expect:
        !event().affectsDefinition(definition('test.Plain'))
        !event('test.Plain').affectsDefinition(null)
    }

    private ClassChangeEvent event(String... classNames) {
        new ClassChangeEvent(this, [] as Set, context.classLoader,
            classNames.collect { new ClassChange(it, ClassChange.Kind.MODIFIED) }, ReloadStrategy.RELOAD)
    }

    private Class<?> type(String name) {
        context.classLoader.loadClass(name)
    }

    private BeanDefinition<?> definition(String name) {
        context.getBeanDefinition(type(name))
    }
}
