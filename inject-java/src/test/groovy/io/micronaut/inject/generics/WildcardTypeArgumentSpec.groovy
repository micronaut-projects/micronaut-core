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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.WildcardArgument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Unroll

import java.util.function.Consumer

class WildcardTypeArgumentSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

interface Foo<T> {}
interface Bounded<T extends Number> {}
class Book {}
abstract class Base<A, B> {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {}

@Singleton
class Bean<B extends Book> extends Base<Foo<?>, List<? super Book>> implements Foo<List<? extends Number>> {
    @Inject Foo<?> field;
    @Inject Map<? extends CharSequence, ? super Book> mapField;

    Bean(Foo<?> unbounded,
         Foo<Object> object,
         Foo<B> variable,
         List<? extends Number> upper,
         Consumer<? super Book> lower,
         Bounded<?> implicitBound,
         List<List<? extends Number>> nested,
         List<? extends @Mark Number> annotated,
         List<? extends B> classVariableBound,
         List<? extends Comparable<String>> parameterizedBound,
         Map<? extends CharSequence, ? super Book> two,
         List<? extends Number>[] array,
         Optional<? extends Foo<?>> optionalNested) {}

    @Inject
    void inject(Foo<?> injected, List<? super Book> lowerInjected) {}

    @Executable
    void on(Foo<?> unbounded, List<? extends Number> upper, Consumer<? super Book> lower) {}

    @Executable
    <M extends Number> void methodVariable(List<? extends M> methodVariableBound, Foo<M> variable) {}

    @Executable
    List<? extends Number> returnsUpper() { return null; }

    @Executable
    Map<? extends CharSequence, ? super Book> returnsTwo() { return null; }

    @Executable
    Foo<Object> returnsObject() { return null; }
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

    void "a type argument that is not a wildcard is not marked"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !(arguments.object.typeParameters[0] instanceof WildcardArgument)
        !arguments.object.typeParameters[0].isTypeVariable()

        and: 'a class type variable stays a type variable'
        arguments.variable.typeParameters[0].isTypeVariable()
        !(arguments.variable.typeParameters[0] instanceof WildcardArgument)

        and: 'the enclosing argument is never marked'
        !(arguments.upper instanceof WildcardArgument)
        !(arguments.nested instanceof WildcardArgument)
    }

    void "a wildcard keeps the type arguments of its bound"() {
        given:
        Argument<?> comparable = constructorArguments().parameterizedBound.typeParameters[0]

        expect:
        comparable.type == Comparable
        upper(comparable) == [comparable.type.name] && lower(comparable) == []
        comparable.typeParameters[0].type == String
        !(comparable.typeParameters[0] instanceof WildcardArgument)
        wildcard(comparable).upperBounds[0].typeParameters[0].type == String
    }

    void "a nested wildcard is recorded at its own level only"() {
        given:
        Map<String, Argument<?>> arguments = constructorArguments()

        expect:
        !(arguments.nested.typeParameters[0] instanceof WildcardArgument)
        arguments.nested.typeParameters[0].type == List
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        upper(arguments.nested.typeParameters[0].typeParameters[0]) == [arguments.nested.typeParameters[0].typeParameters[0].type.name] && lower(arguments.nested.typeParameters[0].typeParameters[0]) == []

        and: 'a wildcard bounded by a type with a wildcard records both'
        Argument<?> foo = arguments.optionalNested.typeParameters[0]
        foo.type.name == 'test.Foo'
        upper(foo) == [foo.type.name] && lower(foo) == []
        foo.typeParameters[0].type == Object
        upper(foo.typeParameters[0]) == ['java.lang.Object'] && lower(foo.typeParameters[0]) == []
    }

    void "each type argument is recorded independently"() {
        given:
        Argument<?> two = constructorArguments().two

        expect:
        two.typeParameters.length == 2
        two.typeParameters[0].type == CharSequence
        upper(two.typeParameters[0]) == [two.typeParameters[0].type.name] && lower(two.typeParameters[0]) == []
        two.typeParameters[1].type.name == 'test.Book'
        lower(two.typeParameters[1]) == [two.typeParameters[1].type.name] && upper(two.typeParameters[1]) == ['java.lang.Object']

        and: 'by name'
        upper(two.typeVariables.K) == [two.typeVariables.K.type.name] && lower(two.typeVariables.K) == []
        lower(two.typeVariables.V) == [two.typeVariables.V.type.name] && upper(two.typeVariables.V) == ['java.lang.Object']
    }

    void "a wildcard inside an array argument is recorded"() {
        given:
        Argument<?> array = constructorArguments().array

        expect:
        array.type == List[]
        array.typeParameters[0].type == Number
        upper(array.typeParameters[0]) == [array.typeParameters[0].type.name] && lower(array.typeParameters[0]) == []
    }

    void "a type-use annotation on the bound is kept next to the wildcard"() {
        given:
        Argument<?> annotated = constructorArguments().annotated.typeParameters[0]

        expect:
        upper(annotated) == [annotated.type.name] && lower(annotated) == []
        annotated.annotationMetadata.hasAnnotation('test.Mark')
        annotated.annotationMetadata.annotationNames == ['test.Mark'] as Set
    }

    void "a wildcard is recorded for an injected field"() {
        given:
        def field = definition.injectedFields.find { it.name == 'field' }
        def mapField = definition.injectedFields.find { it.name == 'mapField' }

        expect:
        upper(field.asArgument().typeParameters[0]) == ['java.lang.Object'] && lower(field.asArgument().typeParameters[0]) == []
        upper(mapField.asArgument().typeParameters[0]) == [mapField.asArgument().typeParameters[0].type.name] && lower(mapField.asArgument().typeParameters[0]) == []
        lower(mapField.asArgument().typeParameters[1]) == [mapField.asArgument().typeParameters[1].type.name] && upper(mapField.asArgument().typeParameters[1]) == ['java.lang.Object']
    }

    void "a wildcard is recorded for an injected method parameter"() {
        given:
        def inject = definition.injectedMethods.find { it.name == 'inject' }
        Map<String, Argument<?>> arguments = inject.arguments.collectEntries { [it.name, it] }

        expect:
        upper(arguments.injected.typeParameters[0]) == ['java.lang.Object'] && lower(arguments.injected.typeParameters[0]) == []
        arguments.lowerInjected.typeParameters[0].type.name == 'test.Book'
        lower(arguments.lowerInjected.typeParameters[0]) == [arguments.lowerInjected.typeParameters[0].type.name] && upper(arguments.lowerInjected.typeParameters[0]) == ['java.lang.Object']
    }

    void "a wildcard is recorded for an executable method parameter"() {
        given:
        Map<String, Argument<?>> arguments = method('on').arguments.collectEntries { [it.name, it] }

        expect:
        upper(arguments.unbounded.typeParameters[0]) == ['java.lang.Object'] && lower(arguments.unbounded.typeParameters[0]) == []
        arguments.upper.typeParameters[0].type == Number
        upper(arguments.upper.typeParameters[0]) == [arguments.upper.typeParameters[0].type.name] && lower(arguments.upper.typeParameters[0]) == []
        arguments.lower.typeParameters[0].type.name == 'test.Book'
        lower(arguments.lower.typeParameters[0]) == [arguments.lower.typeParameters[0].type.name] && upper(arguments.lower.typeParameters[0]) == ['java.lang.Object']
    }

    void "a wildcard bounded by a method type variable is recorded with the variable's bound"() {
        given:
        Map<String, Argument<?>> arguments = method('methodVariable').arguments.collectEntries { [it.name, it] }

        expect:
        arguments.methodVariableBound.typeParameters[0].type == Number
        upper(arguments.methodVariableBound.typeParameters[0]) == [arguments.methodVariableBound.typeParameters[0].type.name] && lower(arguments.methodVariableBound.typeParameters[0]) == []

        and: 'the method type variable itself is not'
        arguments.variable.typeParameters[0].type == Number
        arguments.variable.typeParameters[0].isTypeVariable()
        !(arguments.variable.typeParameters[0] instanceof WildcardArgument)
    }

    void "a wildcard is recorded for an executable method return type"() {
        given:
        Argument<?> returnedUpper = method('returnsUpper').returnType.asArgument()
        Argument<?> two = method('returnsTwo').returnType.asArgument()
        Argument<?> object = method('returnsObject').returnType.asArgument()

        expect:
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

        expect: 'implements Foo<List<? extends Number>>'
        fooArgument.type == List
        !(fooArgument instanceof WildcardArgument)
        fooArgument.typeParameters[0].type == Number
        upper(fooArgument.typeParameters[0]) == [fooArgument.typeParameters[0].type.name] && lower(fooArgument.typeParameters[0]) == []

        and: 'extends Base<Foo<?>, List<? super Book>>'
        baseArguments[0].type.name == 'test.Foo'
        upper(baseArguments[0].typeParameters[0]) == ['java.lang.Object'] && lower(baseArguments[0].typeParameters[0]) == []
        baseArguments[1].type == List
        baseArguments[1].typeParameters[0].type.name == 'test.Book'
        lower(baseArguments[1].typeParameters[0]) == [baseArguments[1].typeParameters[0].type.name] && upper(baseArguments[1].typeParameters[0]) == ['java.lang.Object']
    }

    void "a wildcard is recorded in the type arguments of a factory bean"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Consumer;

interface Foo<T> {}
class Book {}

@Factory
class FooFactory {
    @Bean
    @Singleton
    Foo<List<? extends Number>> foo() {
        return new Foo<List<? extends Number>>() {};
    }

    @Bean
    @Singleton
    Consumer<? super Book> consumer() {
        return b -> {};
    }
}
''')
        def fooDefinition = context.getBeanDefinition(context.classLoader.loadClass('test.Foo'))
        def consumerDefinition = context.getBeanDefinition(Consumer)

        expect:
        upper(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == [fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0].type.name] && lower(fooDefinition.getTypeArguments('test.Foo')[0].typeParameters[0]) == []

        and:
        consumerDefinition.getTypeArguments(Consumer)[0].type.name == 'test.Book'
        lower(consumerDefinition.getTypeArguments(Consumer)[0]) == [consumerDefinition.getTypeArguments(Consumer)[0].type.name] && upper(consumerDefinition.getTypeArguments(Consumer)[0]) == ['java.lang.Object']

        cleanup:
        context.close()
    }

    @PendingFeature(reason = "the wildcard placeholder is named after the container's parameter and resolved through the bean's type variable of the same name")
    void "an unbounded wildcard is not resolved through a bean type variable of the same name"() {
        given:
        BeanDefinition<?> collision = buildBeanDefinition('test.Collision', '''
package test;

import jakarta.inject.Singleton;

interface Foo<T> {}
class Book {}

@Singleton
class Collision<T extends Book> {
    Collision(Foo<?> unbounded) {}
}
''')
        Argument<?> typeArgument = collision.constructor.arguments[0].typeParameters[0]

        expect:
        typeArgument.type == Object
        upper(typeArgument) == ['java.lang.Object'] && lower(typeArgument) == []
    }

    void "a wildcard is recorded for introspected properties, record components and fields"() {
        given:
        BeanIntrospection<?> bean = buildBeanIntrospection('test.Bean', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;

class Book {}

@Introspected
class Bean {
    private List<? extends Number> numbers;
    private Map<? extends CharSequence, ? super Book> map;
    public List<? extends Number> getNumbers() { return numbers; }
    public void setNumbers(List<? extends Number> numbers) { this.numbers = numbers; }
    public Map<? extends CharSequence, ? super Book> getMap() { return map; }
    public void setMap(Map<? extends CharSequence, ? super Book> map) { this.map = map; }
}
''')
        BeanIntrospection<?> record = buildBeanIntrospection('test.Record', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;

interface Foo<T> {}

@Introspected
record Record(List<? extends Number> numbers, Foo<?> foo) {}
''')
        BeanIntrospection<?> fields = buildBeanIntrospection('test.Fields', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Fields {
    public List<? extends Number> numbers;
}
''')

        expect: 'bean properties'
        upper(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
        upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[0].type.name] && lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[0]) == []
        lower(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == [bean.getRequiredProperty('map', Map).asArgument().typeParameters[1].type.name] && upper(bean.getRequiredProperty('map', Map).asArgument().typeParameters[1]) == ['java.lang.Object']

        and: 'record components, as properties and as constructor arguments'
        upper(record.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [record.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(record.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
        upper(record.getProperty('foo').get().asArgument().typeParameters[0]) == ['java.lang.Object'] && lower(record.getProperty('foo').get().asArgument().typeParameters[0]) == []
        upper(record.constructorArguments[0].typeParameters[0]) == [record.constructorArguments[0].typeParameters[0].type.name] && lower(record.constructorArguments[0].typeParameters[0]) == []
        upper(record.constructorArguments[1].typeParameters[0]) == ['java.lang.Object'] && lower(record.constructorArguments[1].typeParameters[0]) == []

        and: 'field access'
        upper(fields.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == [fields.getRequiredProperty('numbers', List).asArgument().typeParameters[0].type.name] && lower(fields.getRequiredProperty('numbers', List).asArgument().typeParameters[0]) == []
    }
}
