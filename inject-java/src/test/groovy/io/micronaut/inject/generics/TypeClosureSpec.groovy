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
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument
import io.micronaut.inject.BeanDefinition

/**
 * {@code BeanDefinition#getTypeClosure()} records every type a bean is assignable to, as the compiler resolved
 * it, so that a consumer needing the hierarchy reads the classes and their arguments from the definition
 * instead of loading a recorded name or walking the hierarchy reflectively.
 */
class TypeClosureSpec extends AbstractTypeElementSpec {

    void "test a generic bean with a bound variable records the variable, its super types in order and Object last"() {
        given:
        def definition = buildBeanDefinition('closure.Holder', '''
package closure;

import jakarta.inject.Singleton;

interface Marker {}

interface Repo<T> extends Marker {}

abstract class Base<X extends Number> implements Repo<X> {}

@Singleton
class Holder<T extends Number> extends Base<T> implements Comparable<Holder<T>> {
    public int compareTo(Holder<T> other) { return 0; }
}
''')
        def closure = definition.typeClosure
        def names = closure*.type*.name

        expect: 'the bean type first, the super class and its own super types before the interfaces, Object last'
        names == ['closure.Holder', 'closure.Base', 'closure.Repo', 'closure.Marker', Comparable.name, Object.name]

        and: 'the open variable of the bean type stays a variable with its bound'
        closure[0].typeParameters.size() == 1
        closure[0].typeParameters[0] instanceof GenericPlaceholder
        closure[0].typeParameters[0].variableName == 'T'
        closure[0].typeParameters[0].type == Number

        and: 'the variable is carried into the super types, still a variable bounded by Number'
        closure[1].typeParameters[0] instanceof GenericPlaceholder
        closure[1].typeParameters[0].type == Number
        closure[2].typeParameters[0] instanceof GenericPlaceholder
        closure[2].typeParameters[0].type == Number

        and: 'a super type that declares no type parameter is recorded all the same'
        closure[3].typeParameters.length == 0
        closure[5] == Argument.OBJECT_ARGUMENT

        and: 'the type argument lookups answer from the same record'
        definition.getTypeArguments('closure.Repo')*.type == [Number]
        definition.getTypeArguments(Comparable)*.type == [definition.beanType]
        definition.typeArgumentKeys.toSet() == ['closure.Holder', 'closure.Base', 'closure.Repo', 'closure.Marker', Comparable.name].toSet()
    }

    void "test a diamond records the shared interface where it was first reached"() {
        given:
        def definition = buildBeanDefinition('closure.Diamond', '''
package closure;

import jakarta.inject.Singleton;

interface Top<T> {}

interface Left<T> extends Top<T> {}

interface Right<T> extends Top<T> {}

@Singleton
class Diamond implements Left<String>, Right<String> {}
''')
        def closure = definition.typeClosure

        expect:
        closure*.type*.name == ['closure.Diamond', 'closure.Left', 'closure.Top', 'closure.Right', Object.name]
        closure.find { it.type.name == 'closure.Top' }.typeParameters*.type == [String]
    }

    void "test a raw super type erases the types above it"() {
        given:
        def definition = buildBeanDefinition('closure.RawBox', '''
package closure;

import jakarta.inject.Singleton;

interface View<E> {}

class Box<T> implements View<T> {}

@Singleton
class RawBox extends Box {}
''')
        def closure = definition.typeClosure

        expect:
        closure*.type*.name == ['closure.RawBox', 'closure.Box', 'closure.View', Object.name]

        and: 'the raw super type and the super type reached through it are raw'
        closure[1].isRawType()
        closure[2].isRawType()
        closure[0].typeParameters.length == 0
    }

    void "test a wildcard is kept as written"() {
        given:
        def definition = buildBeanDefinition('closure.WildRepo', '''
package closure;

import jakarta.inject.Singleton;
import java.util.List;

interface Repo<T> {}

@Singleton
class WildRepo implements Repo<List<? extends Number>> {}
''')
        def repo = definition.typeClosure.find { it.type.name == 'closure.Repo' }

        expect:
        repo.typeParameters*.type == [List]
        repo.typeParameters[0].typeParameters[0] instanceof WildcardArgument
        repo.typeParameters[0].typeParameters[0].upperBounds*.type == [Number]
    }

    void "test a factory-produced bean answers the closure of the produced type"() {
        given:
        def context = buildContext('''
package closure;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

interface View<E> {}

class Box<T> implements View<T> {}

@Factory
class BoxFactory {
    @Bean
    @Singleton
    Box<String> box() { return new Box<>(); }
}
''')
        def box = context.classLoader.loadClass('closure.Box')
        def definition = context.getBeanDefinition(box)
        def closure = definition.typeClosure

        expect:
        closure*.type*.name == ['closure.Box', 'closure.View', Object.name]
        closure[0].typeParameters*.type == [String]
        closure[1].typeParameters*.type == [String]
        !(closure[1].typeParameters[0] instanceof GenericPlaceholder)

        cleanup:
        context.close()
    }

    void "test the closure is about assignability where the exposed types are about lookup"() {
        given:
        def context = buildContext('''
package closure;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Singleton;

interface Repo<T> {}

interface Audited {}

@Singleton
@Bean(typed = Repo.class)
class TypedRepo implements Repo<String>, Audited {}
''')
        def repo = context.classLoader.loadClass('closure.Repo')
        def audited = context.classLoader.loadClass('closure.Audited')
        def typedRepo = context.classLoader.loadClass('closure.TypedRepo')
        def definition = context.getBeanDefinition(repo)

        expect: 'the bean is only looked up by the type it exposes'
        definition.exposedTypes == [repo] as Set
        !context.containsBean(audited)
        !context.containsBean(typedRepo)

        and: 'but is still assignable to everything in its hierarchy'
        definition.typeClosure*.type == [typedRepo, repo, audited, Object]

        cleanup:
        context.close()
    }

    void "test a proxy definition answers for the type it proxies"() {
        given:
        def context = buildContext('''
package closure;

import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

interface Repo<T> {}

@Singleton
class ProxiedRepo implements Repo<Integer> {
    @Mutating("name")
    public String hello(String name) {
        return name;
    }
}
''')
        def proxied = context.classLoader.loadClass('closure.ProxiedRepo')
        def definition = context.getBeanDefinitions(proxied).find { it.isProxy() }

        expect: 'the target type and its super types, not the generated proxy class'
        definition != null
        definition.typeClosure*.type*.name == ['closure.ProxiedRepo', 'closure.Repo', Object.name]
        definition.typeClosure[1].typeParameters*.type == [Integer]

        cleanup:
        context.close()
    }

    void "test an introduction implementing further interfaces is assignable to them too"() {
        given:
        def context = buildContext('''
package closure;

import jakarta.inject.Singleton;
import io.micronaut.aop.introduction.ListenerAdvice;

interface Repo<T> {}

@ListenerAdvice
@Singleton
class AdvisedRepo implements Repo<Integer> {
    public void hello() {}
}
''')
        def advised = context.classLoader.loadClass('closure.AdvisedRepo')
        def definition = context.getBeanDefinition(advised)
        def closure = definition.typeClosure

        expect: 'an introduction on a class is the generated subclass itself, so the closure starts there, then the class and its hierarchy, then the interface the introduction adds and its own super types'
        definition.beanType.name == 'closure.AdvisedRepo$Intercepted'
        closure*.type*.name == [definition.beanType.name, 'closure.AdvisedRepo', 'closure.Repo', ApplicationEventListener.name, EventListener.name, Object.name]

        cleanup:
        context.close()
    }

    void "test a bean with no type argument anywhere records its hierarchy and still answers no keys"() {
        given:
        def definition = buildBeanDefinition('closure.Plain', '''
package closure;

import jakarta.inject.Singleton;

interface Marker {}

@Singleton
class Plain implements Marker {}
''')

        expect:
        definition.typeClosure*.type*.name == ['closure.Plain', 'closure.Marker', Object.name]
        definition.typeArgumentKeys.isEmpty()
        definition.typeArguments.isEmpty()
    }
}
