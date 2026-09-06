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
import io.micronaut.core.annotation.Wildcard
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
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
    void "constructor parameter #name compiles the wildcard to #type marked #bound"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        wildcardOf(typeArgument) == bound

        where:
        name                 | type                   | bound
        'unbounded'          | 'java.lang.Object'     | Wildcard.Bound.NONE
        'upper'              | 'java.lang.Number'     | Wildcard.Bound.UPPER
        'lower'              | 'test.Book'            | Wildcard.Bound.LOWER
        'implicitBound'      | 'java.lang.Number'     | Wildcard.Bound.NONE
        'classVariableBound' | 'test.Book'            | Wildcard.Bound.UPPER
        'parameterizedBound' | 'java.lang.Comparable' | Wildcard.Bound.UPPER
        'object'             | 'java.lang.Object'     | null
        'variable'           | 'test.Book'            | null
    }

    void "a type argument that is not a wildcard is not marked and the enclosing argument never is"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !arguments.object.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.variable.typeParameters[0].isTypeVariable()
        !arguments.variable.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        !arguments.upper.annotationMetadata.hasAnnotation(Wildcard)
        !arguments.nested.annotationMetadata.hasAnnotation(Wildcard)
    }

    void "a wildcard keeps the type arguments of its bound and a nested wildcard is recorded at its own level"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()
        Argument<?> comparable = arguments.parameterizedBound.typeParameters[0]
        Argument<?> foo = arguments.optionalNested.typeParameters[0]

        expect:
        comparable.typeParameters[0].type == String
        !comparable.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)

        and:
        !arguments.nested.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        wildcardOf(arguments.nested.typeParameters[0].typeParameters[0]) == Wildcard.Bound.UPPER

        and:
        foo.type.name == 'test.Foo'
        wildcardOf(foo) == Wildcard.Bound.UPPER
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

    void "a wildcard is recorded for injected fields and injected method parameters"() {
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

    void "a wildcard is recorded for executable method parameters and return types"() {
        given:
        Map<String, Argument<?>> on = method('on').arguments.collectEntries { [it.name, it] }
        Map<String, Argument<?>> methodVariable = method('methodVariable').arguments.collectEntries { [it.name, it] }
        Argument<?> upper = method('returnsUpper').returnType.asArgument()
        Argument<?> two = method('returnsTwo').returnType.asArgument()
        Argument<?> object = method('returnsObject').returnType.asArgument()

        expect:
        wildcardOf(on.unbounded.typeParameters[0]) == Wildcard.Bound.NONE
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
        !object.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
    }

    void "a wildcard is recorded in the type arguments of the bean's interface and superclass"() {
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
        wildcardOf(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == Wildcard.Bound.UPPER
        consumerDefinition.getTypeArguments(Consumer)[0].type.name == 'test.Book'
        wildcardOf(consumerDefinition.getTypeArguments(Consumer)[0]) == Wildcard.Bound.LOWER

        cleanup:
        context.close()
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
        wildcardOf(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
        wildcardOf(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == Wildcard.Bound.LOWER
    }
}
