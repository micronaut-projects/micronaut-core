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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.WildcardArgument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

import java.util.function.Consumer

class WildcardTypeArgumentSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.function.Consumer

interface Foo<T> {}
interface Bounded<T extends Number> {}
class Book {}
abstract class Base<A, B> {}

@Singleton
class Bean<B extends Book> extends Base<Foo<?>, List<? super Book>> implements Foo<List<? extends Number>> {
    @Inject Foo<?> field
    @Inject Map<? extends CharSequence, ? super Book> mapField

    Bean(Foo<?> unbounded,
         Foo<Object> object,
         Foo<B> variable,
         List<? extends Number> upper,
         Consumer<? super Book> lower,
         Bounded<?> implicitBound,
         List<List<? extends Number>> nested,
         List<? extends B> classVariableBound,
         List<? extends Comparable<String>> parameterizedBound,
         Map<? extends CharSequence, ? super Book> two,
         Optional<? extends Foo<?>> optionalNested) {}

    @Inject
    void inject(Foo<?> injected, List<? super Book> lowerInjected) {}

    @Executable
    void on(Foo<?> unbounded, List<? extends Number> upper, Consumer<? super Book> lower) {}

    @Executable
    public <M extends Number> void methodVariable(List<? extends M> methodVariableBound, Foo<M> variable) {}

    @Executable
    List<? extends Number> returnsUpper() { null }

    @Executable
    Map<? extends CharSequence, ? super Book> returnsTwo() { null }

    @Executable
    Foo<Object> returnsObject() { null }
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
    void "constructor parameter #name compiles the wildcard to #type bounded by #upperBounds above and #lowerBounds below"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        upper(typeArgument) == upperBounds
        lower(typeArgument) == lowerBounds

        where:
        name                 | type                   | upperBounds              | lowerBounds
        'unbounded'          | 'java.lang.Object'     | ['java.lang.Object']     | []
        'upper'              | 'java.lang.Number'     | ['java.lang.Number']     | []
        'lower'              | 'test.Book'            | ['java.lang.Object']     | ['test.Book']
        'implicitBound'      | 'java.lang.Number'     | ['java.lang.Object']     | []
        'classVariableBound' | 'test.Book'            | ['test.Book']            | []
        'parameterizedBound' | 'java.lang.Comparable' | ['java.lang.Comparable'] | []
        'object'             | 'java.lang.Object'     | null                     | null
        'variable'           | 'test.Book'            | null                     | null
    }

    void "a type argument that is not a wildcard is not marked and the enclosing argument never is"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !(arguments.object.typeParameters[0] instanceof WildcardArgument)
        arguments.variable.typeParameters[0].isTypeVariable()
        !(arguments.variable.typeParameters[0] instanceof WildcardArgument)
        !(arguments.upper instanceof WildcardArgument)
        !(arguments.nested instanceof WildcardArgument)
    }

    void "a wildcard keeps the type arguments of its bound and a nested wildcard is recorded at its own level"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()
        Argument<?> comparable = arguments.parameterizedBound.typeParameters[0]
        Argument<?> foo = arguments.optionalNested.typeParameters[0]

        expect:
        comparable.typeParameters[0].type == String
        !(comparable.typeParameters[0] instanceof WildcardArgument)
        wildcard(comparable).upperBounds[0].typeParameters[0].type == String

        and:
        !(arguments.nested.typeParameters[0] instanceof WildcardArgument)
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        upper(arguments.nested.typeParameters[0].typeParameters[0]) == [arguments.nested.typeParameters[0].typeParameters[0].type.name] && lower(arguments.nested.typeParameters[0].typeParameters[0]) == []

        and:
        foo.type.name == 'test.Foo'
        upper(foo) == [foo.type.name] && lower(foo) == []
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

    void "a wildcard is recorded for injected fields and injected method parameters"() {
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

    void "a wildcard is recorded for executable method parameters and return types"() {
        given:
        Map<String, Argument<?>> on = method('on').arguments.collectEntries { [it.name, it] }
        Map<String, Argument<?>> methodVariable = method('methodVariable').arguments.collectEntries { [it.name, it] }
        Argument<?> returnedUpper = method('returnsUpper').returnType.asArgument()
        Argument<?> two = method('returnsTwo').returnType.asArgument()
        Argument<?> object = method('returnsObject').returnType.asArgument()

        expect:
        upper(on.unbounded.typeParameters[0]) == ['java.lang.Object'] && lower(on.unbounded.typeParameters[0]) == []
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
        !(object.typeParameters[0] instanceof WildcardArgument)
    }

    void "a wildcard is recorded in the type arguments of the bean's interface and superclass"() {
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

    void "a wildcard is recorded in the type arguments of a factory bean"() {
        given:
        def context = buildContext('''
package test

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.util.function.Consumer

interface Foo<T> {}
class Book {}

@Factory
class FooFactory {
    @Bean
    @Singleton
    Foo<List<? extends Number>> foo() {
        return new Foo<List<? extends Number>>() {}
    }

    @Bean
    @Singleton
    Consumer<? super Book> consumer() {
        return { b -> } as Consumer<? super Book>
    }
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

    void "an argument with a nested wildcard has the same type as the argument with the type it is bounded by"() {
        given:
        BeanDefinition<?> client = buildBeanDefinition('test.HeadersClient', '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
class HeadersClient {
    @Executable
    void send(Map<String, ? extends Object> headers, List<Map<String, ? extends Number>> nested, Map<String, Object> plain) {}
}
''')
        Argument<?>[] arguments = client.executableMethods.find { it.methodName == 'send' }.arguments
        Argument<?> headers = arguments[0]
        Argument<?> nested = arguments[1]
        Argument<?> plain = arguments[2]

        expect:
        headers.typeParameters[1] instanceof WildcardArgument
        headers.equalsType(Argument.mapOf(String, Object))
        Argument.mapOf(String, Object).equalsType(headers)
        headers.typeHashCode() == Argument.mapOf(String, Object).typeHashCode()
        headers.equalsType(plain) && plain.equalsType(headers)
        headers.typeHashCode() == plain.typeHashCode()
        !headers.equalsType(Argument.mapOf(String, String))

        and: 'at any depth, the map named after the type parameter of List it stands for'
        nested.equalsType(Argument.listOf(Argument.mapOf(String, Number).withName('E')))
        nested.typeHashCode() == Argument.listOf(Argument.mapOf(String, Number).withName('E')).typeHashCode()

        and: 'equals still tells the wildcard apart'
        headers.typeParameters[1] != plain.typeParameters[1]
        plain.typeParameters[1] != headers.typeParameters[1]
    }

    void "a wildcard is recorded for introspected properties"() {
        given:
        BeanIntrospection<?> bean = buildBeanIntrospection('test.Bean', '''
package test

import io.micronaut.core.annotation.Introspected

class Book {}

@Introspected
class Bean {
    List<? extends Number> numbers
    Map<? extends CharSequence, ? super Book> map
}
''')

        expect:
        upper(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
        upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == []
        lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[1].type.name] && upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == ['java.lang.Object']
    }
}
