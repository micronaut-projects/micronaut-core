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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Unroll

import java.util.function.Consumer

class WildcardTypeArgumentSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Optional
import java.util.function.Consumer

interface Foo<T>
interface Bounded<T : Number>
open class Book
abstract class Base<A, B>

@Singleton
class Bean<B : Book>(
    val star: Foo<*>,
    val any: Foo<Any>,
    val variable: Foo<B>,
    val upper: java.util.List<out Number>,
    val lower: Consumer<in Book>,
    val implicitBound: Bounded<*>,
    val nested: java.util.List<java.util.List<out Number>>,
    val classVariableBound: java.util.List<out B>,
    val parameterizedBound: java.util.List<out Comparable<String>>,
    val two: java.util.Map<out CharSequence, in Book>,
    val optionalNested: Optional<out Foo<*>>
) : Base<Foo<*>, java.util.List<in Book>>(), Foo<java.util.List<out Number>> {

    @Inject
    lateinit var field: Foo<*>

    @Inject
    lateinit var mapField: java.util.Map<out CharSequence, in Book>

    @Inject
    fun inject(injected: Foo<*>, lowerInjected: java.util.List<in Book>) {}

    @Executable
    fun on(star: Foo<*>, upper: java.util.List<out Number>, lower: Consumer<in Book>) {}

    @Executable
    fun <M : Number> methodVariable(methodVariableBound: java.util.List<out M>, variable: Foo<M>) {}

    @Executable
    fun returnsUpper(): java.util.List<out Number>? = null

    @Executable
    fun returnsTwo(): java.util.Map<out CharSequence, in Book>? = null

    @Executable
    fun returnsAny(): Foo<Any>? = null
}
'''

    @Shared BeanDefinition<?> definition

    def setupSpec() {
        definition = buildBeanDefinition('test.Bean', SOURCE)
    }

    private static Wildcard.Bound wildcardOf(Argument<?> argument) {
        argument.annotationMetadata.enumValue(Wildcard, "bound", Wildcard.Bound).orElse(null)
    }

    private Map<String, Argument<?>> constructorArguments() {
        definition.constructor.arguments.collectEntries { [it.name, it] }
    }

    private ExecutableMethod<?, ?> method(String name) {
        definition.executableMethods.find { it.methodName == name }
    }

    @Unroll
    void "constructor parameter #name compiles the projection to #type marked #bound"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        wildcardOf(typeArgument) == bound

        where:
        name                 | type                   | bound
        'star'               | 'java.lang.Object'     | Wildcard.Bound.NONE
        'upper'              | 'java.lang.Number'     | Wildcard.Bound.UPPER
        'lower'              | 'test.Book'            | Wildcard.Bound.LOWER
        'implicitBound'      | 'java.lang.Number'     | Wildcard.Bound.NONE
        'classVariableBound' | 'test.Book'            | Wildcard.Bound.UPPER
        'parameterizedBound' | 'java.lang.Comparable' | Wildcard.Bound.UPPER
        'any'                | 'java.lang.Object'     | null
        'variable'           | 'test.Book'            | null
    }

    void "a type argument that is not a projection is not marked and the enclosing argument never is"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !arguments.any.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.variable.typeParameters[0].isTypeVariable()
        !arguments.variable.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        !arguments.upper.annotationMetadata.hasAnnotation(Wildcard)
        !arguments.nested.annotationMetadata.hasAnnotation(Wildcard)
    }

    void "a projection keeps the type arguments of its bound and a nested projection is recorded at its own level"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()
        Argument<?> comparable = arguments.parameterizedBound.typeParameters[0]
        Argument<?> foo = arguments.optionalNested.typeParameters[0]

        expect:
        comparable.type == Comparable
        wildcardOf(comparable) == Wildcard.Bound.UPPER

        and:
        !arguments.nested.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        wildcardOf(arguments.nested.typeParameters[0].typeParameters[0]) == Wildcard.Bound.UPPER

        and:
        foo.type.name == 'test.Foo'
        wildcardOf(foo) == Wildcard.Bound.UPPER
    }

    @PendingFeature(reason = "the Kotlin processor drops the type arguments of a projection's bound")
    void "a projection keeps the type arguments of its bound"() {
        given:
        Argument<?> comparable = constructorArguments().parameterizedBound.typeParameters[0]

        Argument<?> foo = constructorArguments().optionalNested.typeParameters[0]

        expect:
        comparable.typeParameters[0].type == String
        wildcardOf(foo.typeParameters[0]) == Wildcard.Bound.NONE
    }

    void "each type argument is recorded independently"() {
        given:
        Argument<?> two = constructorArguments().two

        expect:
        two.typeParameters[0].type == CharSequence
        wildcardOf(two.typeParameters[0]) == Wildcard.Bound.UPPER
        two.typeParameters[1].type.name == 'test.Book'
        wildcardOf(two.typeParameters[1]) == Wildcard.Bound.LOWER
        wildcardOf(two.typeVariables.K) == Wildcard.Bound.UPPER
        wildcardOf(two.typeVariables.V) == Wildcard.Bound.LOWER
    }

    void "a projection is recorded for injected fields and injected method parameters"() {
        given:
        // a property is injected through its setter
        Argument<?> field = definition.injectedMethods.find { it.name == 'setField' }.arguments[0]
        Argument<?> mapField = definition.injectedMethods.find { it.name == 'setMapField' }.arguments[0]
        def inject = definition.injectedMethods.find { it.name == 'inject' }
        Map<String, Argument<?>> arguments = inject.arguments.collectEntries { [it.name, it] }

        expect:
        wildcardOf(field.typeParameters[0]) == Wildcard.Bound.NONE
        wildcardOf(mapField.typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(mapField.typeParameters[1]) == Wildcard.Bound.LOWER

        and:
        wildcardOf(arguments.injected.typeParameters[0]) == Wildcard.Bound.NONE
        arguments.lowerInjected.typeParameters[0].type.name == 'test.Book'
        wildcardOf(arguments.lowerInjected.typeParameters[0]) == Wildcard.Bound.LOWER
    }

    void "a projection is recorded for executable method parameters and return types"() {
        given:
        Map<String, Argument<?>> on = method('on').arguments.collectEntries { [it.name, it] }
        Map<String, Argument<?>> methodVariable = method('methodVariable').arguments.collectEntries { [it.name, it] }
        Argument<?> upper = method('returnsUpper').returnType.asArgument()
        Argument<?> two = method('returnsTwo').returnType.asArgument()
        Argument<?> any = method('returnsAny').returnType.asArgument()

        expect:
        wildcardOf(on.star.typeParameters[0]) == Wildcard.Bound.NONE
        on.upper.typeParameters[0].type == Number
        wildcardOf(on.upper.typeParameters[0]) == Wildcard.Bound.UPPER
        on.lower.typeParameters[0].type.name == 'test.Book'
        wildcardOf(on.lower.typeParameters[0]) == Wildcard.Bound.LOWER

        and: 'bounded by a method type variable'
        methodVariable.methodVariableBound.typeParameters[0].type == Number
        wildcardOf(methodVariable.methodVariableBound.typeParameters[0]) == Wildcard.Bound.UPPER
        !methodVariable.variable.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)

        and: 'return types'
        upper.typeParameters[0].type == Number
        wildcardOf(upper.typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(two.typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(two.typeParameters[1]) == Wildcard.Bound.LOWER
        !any.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
    }

    void "a projection is recorded in the type arguments of the bean's interface and superclass"() {
        given:
        Argument<?> fooArgument = definition.getTypeArguments('test.Foo')[0]
        List<Argument<?>> baseArguments = definition.getTypeArguments('test.Base')

        expect:
        fooArgument.type == List
        !fooArgument.annotationMetadata.hasAnnotation(Wildcard)
        fooArgument.typeParameters[0].type == Number
        wildcardOf(fooArgument.typeParameters[0]) == Wildcard.Bound.UPPER

        and:
        baseArguments[0].type.name == 'test.Foo'
        wildcardOf(baseArguments[0].typeParameters[0]) == Wildcard.Bound.NONE
        baseArguments[1].typeParameters[0].type.name == 'test.Book'
        wildcardOf(baseArguments[1].typeParameters[0]) == Wildcard.Bound.LOWER
    }

    void "a projection is recorded in the type arguments of a factory bean"() {
        given:
        def context = buildContext('''
package test

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.util.function.Consumer

interface Foo<T>
open class Book

@Factory
class FooFactory {
    @Bean
    @Singleton
    fun foo(): Foo<java.util.List<out Number>> = object : Foo<java.util.List<out Number>> {}

    @Bean
    @Singleton
    fun consumer(): Consumer<in Book> = Consumer<Book> {}
}
''')
        def fooDefinition = context.getBeanDefinition(context.classLoader.loadClass('test.Foo'))
        def consumerDefinition = context.getBeanDefinition(Consumer)

        expect:
        wildcardOf(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == Wildcard.Bound.UPPER
        consumerDefinition.getTypeArguments(Consumer)[0].type.name == 'test.Book'
        wildcardOf(consumerDefinition.getTypeArguments(Consumer)[0]) == Wildcard.Bound.LOWER

        cleanup:
        context.close()
    }

    void "a projection is recorded for introspected properties of a class and a data class"() {
        given:
        BeanIntrospection<?> bean = buildBeanIntrospection('test.Bean', '''
package test

import io.micronaut.core.annotation.Introspected

open class Book

@Introspected
class Bean {
    var numbers: java.util.List<out Number>? = null
    var map: java.util.Map<out CharSequence, in Book>? = null
}
''')
        BeanIntrospection<?> data = buildBeanIntrospection('test.Data', '''
package test

import io.micronaut.core.annotation.Introspected

interface Foo<T>

@Introspected
data class Data(val numbers: java.util.List<out Number>, val foo: Foo<*>)
''')

        expect:
        wildcardOf(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == Wildcard.Bound.LOWER

        and:
        wildcardOf(data.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(data.getProperty('foo').get().asArgument().typeParameters[0]) == Wildcard.Bound.NONE
        wildcardOf(data.constructorArguments[0].typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(data.constructorArguments[1].typeParameters[0]) == Wildcard.Bound.NONE
    }
}
