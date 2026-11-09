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
package io.micronaut.inject.provider

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException

class BeanProviderCreatedSingletonSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
class Holder {
    final BeanProvider<Single> single;
    final BeanProvider<Proto> proto;
    final BeanProvider<Shape> shape;

    Holder(BeanProvider<Single> single, BeanProvider<Proto> proto, BeanProvider<Shape> shape) {
        this.single = single;
        this.proto = proto;
        this.shape = shape;
    }
}

@Singleton
class Single {
}

@Prototype
class Proto {
}

interface Shape {
}

@Singleton
@Named("circle")
class Circle implements Shape {
}

@Singleton
@Named("square")
class Square implements Shape {
}
'''

    void "a provider returns the singleton the context holds, created before or by the provider"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')

        when: "the provider creates the singleton"
        def first = holder.single.get()

        then: "later calls return the same instance as the context does"
        holder.single.get().is(first)
        getBean(context, 'test.Single').is(first)

        cleanup:
        context.close()
    }

    void "a provider of a destroyed singleton returns the instance created after it"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')
        def first = holder.single.get()

        when:
        context.destroyBean(first)
        def second = holder.single.get()

        then:
        !second.is(first)
        holder.single.get().is(second)
        getBean(context, 'test.Single').is(second)

        cleanup:
        context.close()
    }

    void "a provider of a prototype creates an instance on every call"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')

        expect:
        !holder.proto.get().is(holder.proto.get())

        cleanup:
        context.close()
    }

    void "a provider of an ambiguous type reports the ambiguity even once the candidates exist"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')
        context.getBeansOfType(context.classLoader.loadClass('test.Shape'))

        when:
        holder.shape.get()

        then:
        thrown(NonUniqueBeanException)

        cleanup:
        context.close()
    }
}
