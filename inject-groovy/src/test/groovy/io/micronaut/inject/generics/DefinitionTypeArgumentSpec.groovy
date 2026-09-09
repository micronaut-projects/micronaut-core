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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * A bean definition's own type argument answers what the source wrote: a binding of a concrete type is an
 * ordinary argument, a parameter the bean leaves unbound is a placeholder of that name, and a bean type
 * written without its type arguments is raw.
 */
class DefinitionTypeArgumentSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

class Box<T extends Number> {}

@Factory
class Boxes {

    @Singleton
    Box<Integer> typed() { new Box<Integer>() }

    @Singleton
    Box raw() { new Box() }
}

@Singleton
class OpenBox<T extends Number> extends Box<T> {}

@Singleton
class ClosedBox extends Box<Integer> {}
'''

    @Shared @AutoCleanup ApplicationContext context
    @Shared Class<?> boxType

    def setupSpec() {
        context = buildContext(SOURCE)
        boxType = context.classLoader.loadClass('test.Box')
    }

    private BeanDefinition<?> definitionOf(String beanTypeName) {
        context.getBeanDefinitions(boxType).find { it.beanType.name == beanTypeName }
    }

    private BeanDefinition<?> producedBy(String factoryMethod) {
        context.getBeanDefinitions(boxType).find {
            it.beanType.name == 'test.Box' && it.getBeanDefinitionName().contains(factoryMethod)
        }
    }

    void "a concrete binding on a definition is not read as a type variable"() {
        given:
        Argument<?> argument = producedBy('Typed').asArgument()

        expect: 'the bean type itself was written with its type arguments'
        !argument.isRawType()
        !argument.isTypeVariable()

        and: 'and the argument bound to the declaration parameter is the type that was written there'
        argument.typeParameters.length == 1
        argument.typeParameters[0].type == Integer
        !argument.typeParameters[0].isTypeVariable()
        !argument.typeParameters[0].isRawType()
        !(argument.typeParameters[0] instanceof GenericPlaceholder)
    }

    void "a parameter the bean leaves unbound is read as a type variable"() {
        given:
        Argument<?> argument = definitionOf('test.OpenBox').asArgument()

        expect:
        !argument.isRawType()
        argument.typeParameters.length == 1

        and: 'the type argument stands for the variable the class declares, bounded as it declared it'
        argument.typeParameters[0].isTypeVariable()
        argument.typeParameters[0] instanceof GenericPlaceholder
        ((GenericPlaceholder) argument.typeParameters[0]).variableName == 'T'
        argument.typeParameters[0].type == Number
    }

    void "a raw producer's argument is raw"() {
        given:
        Argument<?> argument = producedBy('Raw').asArgument()

        expect: 'the bean type was written without its type arguments'
        argument.isRawType()

        and: 'it keeps the type arguments the declaring type declares, as a raw injection point does'
        argument.typeParameters.length == 1
        argument.typeParameters[0].isTypeVariable()
        ((GenericPlaceholder) argument.typeParameters[0]).variableName == 'T'
    }

    void "a bean class binding the parameter of its super type is not read as a type variable"() {
        given:
        BeanDefinition<?> definition = definitionOf('test.ClosedBox')

        expect: 'the bean type declares no parameter of its own'
        !definition.asArgument().isRawType()
        definition.asArgument().typeParameters.length == 0

        and: 'and the binding it gives its super type is the type that was written there'
        definition.getTypeArguments(boxType).size() == 1
        definition.getTypeArguments(boxType)[0].type == Integer
        !definition.getTypeArguments(boxType)[0].isTypeVariable()
    }

    void "the type arguments of a definition stay keyed by the name of the parameter they bind"() {
        expect: 'the argument is named after the parameter it stands in for, whatever is bound to it'
        producedBy('Typed').getTypeArguments(boxType)[0].name == 'T'
        producedBy('Raw').getTypeArguments(boxType)[0].name == 'T'
        definitionOf('test.OpenBox').getTypeArguments(boxType)[0].name == 'T'
        definitionOf('test.ClosedBox').getTypeArguments(boxType)[0].name == 'T'

        and: 'and reachable by that name from the bean type as an argument'
        producedBy('Typed').asArgument().typeVariables.keySet() == ['T'] as Set
        definitionOf('test.OpenBox').asArgument().typeVariables.keySet() == ['T'] as Set
    }

    void "a bean is still resolved by the type arguments of its definition"() {
        expect: 'the two definitions that bind the parameter to Integer are the ones selected by it'
        context.getBeanDefinitions(boxType, Qualifiers.byExactTypeArgumentName(Integer.name))*.beanType.name.toSorted() ==
            ['test.Box', 'test.ClosedBox']

        and: 'a definition that leaves the parameter unbound still matches by assignability'
        context.getBeanDefinitions(boxType, Qualifiers.byTypeArguments(Integer))*.beanType.name.toSorted() ==
            ['test.Box', 'test.Box', 'test.ClosedBox', 'test.OpenBox']
    }
}
