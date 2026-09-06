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
import io.micronaut.core.annotation.Wildcard
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

class WildcardTypeArgumentSpec extends AbstractKotlinCompilerSpec {

    private static Wildcard.Bound wildcardOf(Argument<?> argument) {
        argument.annotationMetadata.enumValue(Wildcard, "bound", Wildcard.Bound).orElse(null)
    }

    void "a projected type argument is recorded on the argument it is compiled to"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Bean', '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import java.util.function.Consumer

interface Foo<T>
class Book

@Singleton
class Bean(val star: Foo<*>, val any: Foo<Any>, val upper: java.util.List<out Number>, val lower: Consumer<in Book>) {

    @Executable
    fun on(star: Foo<*>, upper: java.util.List<out Number>, lower: Consumer<in Book>) {}
}
''')
        Map<String, Argument<?>> arguments = definition.constructor.arguments.collectEntries { [it.name, it] }
        def method = definition.executableMethods.find { it.methodName == 'on' }
        Map<String, Argument<?>> methodArguments = method.arguments.collectEntries { [it.name, it] }

        expect:
        arguments.star.typeParameters[0].type == Object
        wildcardOf(arguments.star.typeParameters[0]) == Wildcard.Bound.NONE
        arguments.upper.typeParameters[0].type == Number
        wildcardOf(arguments.upper.typeParameters[0]) == Wildcard.Bound.UPPER
        arguments.lower.typeParameters[0].type.name == 'test.Book'
        wildcardOf(arguments.lower.typeParameters[0]) == Wildcard.Bound.LOWER
        !arguments.any.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)

        and:
        wildcardOf(methodArguments.star.typeParameters[0]) == Wildcard.Bound.NONE
        wildcardOf(methodArguments.upper.typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(methodArguments.lower.typeParameters[0]) == Wildcard.Bound.LOWER
    }
}
