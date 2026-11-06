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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import spock.lang.Shared

/**
 * Kotlin has no raw types: a type is always written with its type arguments, and a star projection is
 * {@code List<?>} rather than a raw {@code List}. The Kotlin element model still marks the argument a star
 * projection compiles to raw, so this pins that none of it reaches the argument as
 * {@link Argument#isRawType()}, while a usage written with a type variable stays one.
 */
class RawTypeArgumentSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Singleton

interface Container<E>

class Box<B : Number>

@Singleton
class Holder<T : Number>(
    val variableConstructorArgument: Container<T>
) {

    @Inject
    lateinit var variable: Container<T>

    @Inject
    lateinit var concrete: Container<String>

    @Inject
    lateinit var star: Container<*>

    @Inject
    lateinit var box: Box<Number>

    @Inject
    fun inject(injected: Container<T>) {}

    @Executable
    fun returns(): Container<T>? = null
}
'''

    @Shared BeanDefinition<?> definition

    def setupSpec() {
        definition = buildBeanDefinition('test.Holder', SOURCE)
    }

    /**
     * An injected Kotlin property is injected through its setter, so the argument is the setter's parameter.
     */
    private Argument<?> property(String name) {
        definition.injectedMethods.find { it.name == 'set' + name.capitalize() }.arguments[0]
    }

    void "a usage written with a type variable is a variable and is not raw"() {
        given:
        Argument<?> variable = property('variable')

        expect:
        !variable.isRawType()
        variable.typeParameters[0] instanceof GenericPlaceholder
        variable.typeParameters[0].isTypeVariable()
        variable.typeParameters[0].type == Number
    }

    void "no Kotlin usage is raw, a star projection included"() {
        expect:
        !property('concrete').isRawType()
        !property('star').isRawType()
        !property('box').isRawType()

        and: 'nor the type argument the star projection compiles to'
        !property('star').typeParameters[0].isRawType()
        property('star').typeParameters[0].type == Object
    }

    void "a constructor argument, method argument and return type written with a variable are not raw"() {
        given:
        Argument<?> constructorArgument = definition.constructor.arguments.find { it.name == 'variableConstructorArgument' }
        Argument<?> methodArgument = definition.injectedMethods.find { it.name == 'inject' }.arguments[0]
        Argument<?> returnType = definition.executableMethods.find { it.methodName == 'returns' }.returnType.asArgument()

        expect:
        !constructorArgument.isRawType()
        !methodArgument.isRawType()
        !returnType.isRawType()

        and: 'and each keeps the variable it was written with, erased to the bound it declares'
        constructorArgument.typeParameters[0].isTypeVariable()
        methodArgument.typeParameters[0].isTypeVariable()
        returnType.typeParameters[0].isTypeVariable()
        constructorArgument.typeParameters[0].type == Number
    }
}
