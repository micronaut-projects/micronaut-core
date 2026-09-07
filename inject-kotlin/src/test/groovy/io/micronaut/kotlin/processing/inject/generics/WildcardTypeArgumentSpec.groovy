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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.WildcardArgument
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

    private static WildcardArgument<?> wildcard(Argument<?> argument) {
        argument instanceof WildcardArgument ? (WildcardArgument<?>) argument : null
    }

    private static List<String> upper(Argument<?> argument) {
        wildcard(argument)?.upperBounds*.type*.name
    }

    private static List<String> lower(Argument<?> argument) {
        wildcard(argument)?.lowerBounds*.type*.name
    }

    private Map<String, Argument<?>> constructorArguments() {
        definition.constructor.arguments.collectEntries { [it.name, it] }
    }

    private ExecutableMethod<?, ?> method(String name) {
        definition.executableMethods.find { it.methodName == name }
    }

    @Unroll
    void "constructor parameter #name compiles the projection to #type bounded by #upperBounds above and #lowerBounds below"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        upper(typeArgument) == upperBounds
        lower(typeArgument) == lowerBounds

        where:
        name                 | type                   | upperBounds              | lowerBounds
        'star'               | 'java.lang.Object'     | ['java.lang.Object']     | []
        'upper'              | 'java.lang.Number'     | ['java.lang.Number']     | []
        'lower'              | 'test.Book'            | ['java.lang.Object']     | ['test.Book']
        'implicitBound'      | 'java.lang.Number'     | ['java.lang.Object']     | []
        'classVariableBound' | 'test.Book'            | ['test.Book']            | []
        'parameterizedBound' | 'java.lang.Comparable' | ['java.lang.Comparable'] | []
        'any'                | 'java.lang.Object'     | null                     | null
        'variable'           | 'test.Book'            | null                     | null
    }

    void "a type argument that is not a projection is not marked and the enclosing argument never is"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !(arguments.any.typeParameters[0] instanceof WildcardArgument)
        arguments.variable.typeParameters[0].isTypeVariable()
        !(arguments.variable.typeParameters[0] instanceof WildcardArgument)
        !(arguments.upper instanceof WildcardArgument)
        !(arguments.nested instanceof WildcardArgument)
    }

    void "a projection keeps the type arguments of its bound and a nested projection is recorded at its own level"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()
        Argument<?> comparable = arguments.parameterizedBound.typeParameters[0]
        Argument<?> foo = arguments.optionalNested.typeParameters[0]

        expect:
        comparable.type == Comparable
        upper(comparable) == [comparable.type.name] && lower(comparable) == []

        and:
        !(arguments.nested.typeParameters[0] instanceof WildcardArgument)
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        upper(arguments.nested.typeParameters[0].typeParameters[0]) == [arguments.nested.typeParameters[0].typeParameters[0].type.name] && lower(arguments.nested.typeParameters[0].typeParameters[0]) == []

        and:
        foo.type.name == 'test.Foo'
        upper(foo) == [foo.type.name] && lower(foo) == []
    }

    @PendingFeature(reason = "the Kotlin processor drops the type arguments of a projection's bound")
    void "a projection keeps the type arguments of its bound"() {
        given:
        Argument<?> comparable = constructorArguments().parameterizedBound.typeParameters[0]

        Argument<?> foo = constructorArguments().optionalNested.typeParameters[0]

        expect:
        comparable.typeParameters[0].type == String
        upper(foo.typeParameters[0]) == ['java.lang.Object'] && lower(foo.typeParameters[0]) == []
    }

    void "each type argument is recorded independently"() {
        given:
        Argument<?> two = constructorArguments().two

        expect:
        two.typeParameters[0].type == CharSequence
        upper(two.typeParameters[0]) == [two.typeParameters[0].type.name] && lower(two.typeParameters[0]) == []
        two.typeParameters[1].type.name == 'test.Book'
        lower(two.typeParameters[1]) == [two.typeParameters[1].type.name] && upper(two.typeParameters[1]) == ['java.lang.Object']
        upper(two.typeVariables.K) == [two.typeVariables.K.type.name] && lower(two.typeVariables.K) == []
        lower(two.typeVariables.V) == [two.typeVariables.V.type.name] && upper(two.typeVariables.V) == ['java.lang.Object']
    }

    void "a projection is recorded for injected fields and injected method parameters"() {
        given:
        // a property is injected through its setter
        Argument<?> field = definition.injectedMethods.find { it.name == 'setField' }.arguments[0]
        Argument<?> mapField = definition.injectedMethods.find { it.name == 'setMapField' }.arguments[0]
        def inject = definition.injectedMethods.find { it.name == 'inject' }
        Map<String, Argument<?>> arguments = inject.arguments.collectEntries { [it.name, it] }

        expect:
        upper(field.typeParameters[0]) == ['java.lang.Object'] && lower(field.typeParameters[0]) == []
        upper(mapField.typeParameters[0]) == [mapField.typeParameters[0].type.name] && lower(mapField.typeParameters[0]) == []
        lower(mapField.typeParameters[1]) == [mapField.typeParameters[1].type.name] && upper(mapField.typeParameters[1]) == ['java.lang.Object']

        and:
        upper(arguments.injected.typeParameters[0]) == ['java.lang.Object'] && lower(arguments.injected.typeParameters[0]) == []
        arguments.lowerInjected.typeParameters[0].type.name == 'test.Book'
        lower(arguments.lowerInjected.typeParameters[0]) == [arguments.lowerInjected.typeParameters[0].type.name] && upper(arguments.lowerInjected.typeParameters[0]) == ['java.lang.Object']
    }

    void "a projection is recorded for executable method parameters and return types"() {
        given:
        Map<String, Argument<?>> on = method('on').arguments.collectEntries { [it.name, it] }
        Map<String, Argument<?>> methodVariable = method('methodVariable').arguments.collectEntries { [it.name, it] }
        Argument<?> returnedUpper = method('returnsUpper').returnType.asArgument()
        Argument<?> two = method('returnsTwo').returnType.asArgument()
        Argument<?> any = method('returnsAny').returnType.asArgument()

        expect:
        upper(on.star.typeParameters[0]) == ['java.lang.Object'] && lower(on.star.typeParameters[0]) == []
        on.upper.typeParameters[0].type == Number
        upper(on.upper.typeParameters[0]) == [on.upper.typeParameters[0].type.name] && lower(on.upper.typeParameters[0]) == []
        on.lower.typeParameters[0].type.name == 'test.Book'
        lower(on.lower.typeParameters[0]) == [on.lower.typeParameters[0].type.name] && upper(on.lower.typeParameters[0]) == ['java.lang.Object']

        and: 'bounded by a method type variable'
        methodVariable.methodVariableBound.typeParameters[0].type == Number
        upper(methodVariable.methodVariableBound.typeParameters[0]) == [methodVariable.methodVariableBound.typeParameters[0].type.name] && lower(methodVariable.methodVariableBound.typeParameters[0]) == []
        !(methodVariable.variable.typeParameters[0] instanceof WildcardArgument)

        and: 'return types'
        returnedUpper.typeParameters[0].type == Number
        upper(returnedUpper.typeParameters[0]) == [returnedUpper.typeParameters[0].type.name] && lower(returnedUpper.typeParameters[0]) == []
        upper(two.typeParameters[0]) == [two.typeParameters[0].type.name] && lower(two.typeParameters[0]) == []
        lower(two.typeParameters[1]) == [two.typeParameters[1].type.name] && upper(two.typeParameters[1]) == ['java.lang.Object']
        !(any.typeParameters[0] instanceof WildcardArgument)
    }

    void "a projection is recorded in the type arguments of the bean's interface and superclass"() {
        given:
        Argument<?> fooArgument = definition.getTypeArguments('test.Foo')[0]
        List<Argument<?>> baseArguments = definition.getTypeArguments('test.Base')

        expect:
        fooArgument.type == List
        !(fooArgument instanceof WildcardArgument)
        fooArgument.typeParameters[0].type == Number
        upper(fooArgument.typeParameters[0]) == [fooArgument.typeParameters[0].type.name] && lower(fooArgument.typeParameters[0]) == []

        and:
        baseArguments[0].type.name == 'test.Foo'
        upper(baseArguments[0].typeParameters[0]) == ['java.lang.Object'] && lower(baseArguments[0].typeParameters[0]) == []
        baseArguments[1].typeParameters[0].type.name == 'test.Book'
        lower(baseArguments[1].typeParameters[0]) == [baseArguments[1].typeParameters[0].type.name] && upper(baseArguments[1].typeParameters[0]) == ['java.lang.Object']
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
        upper(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == [fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0].type.name] && lower(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == []
        consumerDefinition.getTypeArguments(Consumer)[0].type.name == 'test.Book'
        lower(consumerDefinition.getTypeArguments(Consumer)[0]) == [consumerDefinition.getTypeArguments(Consumer)[0].type.name] && upper(consumerDefinition.getTypeArguments(Consumer)[0]) == ['java.lang.Object']

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
        upper(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
        upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == []
        lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[1].type.name] && upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == ['java.lang.Object']

        and:
        upper(data.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [data.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(data.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
        upper(data.getProperty('foo').get().asArgument().typeParameters[0]) == ['java.lang.Object'] && lower(data.getProperty('foo').get().asArgument().typeParameters[0]) == []
        upper(data.constructorArguments[0].typeParameters[0]) == [data.constructorArguments[0].typeParameters[0].type.name] && lower(data.constructorArguments[0].typeParameters[0]) == []
        upper(data.constructorArguments[1].typeParameters[0]) == ['java.lang.Object'] && lower(data.constructorArguments[1].typeParameters[0]) == []
    }
}
