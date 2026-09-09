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
package io.micronaut.kotlin.processing.inject.generics

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * A bean definition's own type argument answers what the source wrote: a binding of a concrete type is an
 * ordinary argument and a parameter the bean leaves unbound is a placeholder of that name. Kotlin has no raw
 * types, so the third of the shapes the Java and Groovy specs pin has no Kotlin equivalent.
 */
class DefinitionTypeArgumentSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

open class Box<T : Number>

@Factory
class Boxes {

    @Singleton
    fun typed(): Box<Int> = Box()
}

@Singleton
open class OpenBox<T : Number> : Box<T>()

@Singleton
class ClosedBox : Box<Int>()
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

    void "a concrete binding on a definition is not read as a type variable"() {
        given:
        Argument<?> argument = definitionOf('test.Box').asArgument()

        expect:
        !argument.isRawType()
        !argument.isTypeVariable()

        and: 'the argument bound to the declaration parameter is the type that was written there'
        argument.typeParameters.length == 1
        argument.typeParameters[0].type == Integer
        !argument.typeParameters[0].isTypeVariable()
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

    void "a bean class binding the parameter of its super type is not read as a type variable"() {
        given:
        BeanDefinition<?> definition = definitionOf('test.ClosedBox')

        expect:
        definition.asArgument().typeParameters.length == 0
        definition.getTypeArguments(boxType).size() == 1
        definition.getTypeArguments(boxType)[0].type == Integer
        !definition.getTypeArguments(boxType)[0].isTypeVariable()
    }

    void "the type arguments of a definition stay keyed by the name of the parameter they bind"() {
        expect:
        definitionOf('test.Box').getTypeArguments(boxType)[0].name == 'T'
        definitionOf('test.OpenBox').getTypeArguments(boxType)[0].name == 'T'
        definitionOf('test.ClosedBox').getTypeArguments(boxType)[0].name == 'T'

        and:
        definitionOf('test.Box').asArgument().typeVariables.keySet() == ['T'] as Set
        definitionOf('test.OpenBox').asArgument().typeVariables.keySet() == ['T'] as Set
    }

    void "a bean is still resolved by the type arguments of its definition"() {
        expect: 'the two definitions that bind the parameter to Int are the ones selected by it'
        context.getBeanDefinitions(boxType, Qualifiers.byExactTypeArgumentName(Integer.name))*.beanType.name.toSorted() ==
            ['test.Box', 'test.ClosedBox']
    }
}
