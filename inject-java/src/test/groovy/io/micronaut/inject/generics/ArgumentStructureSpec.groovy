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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Shared
import spock.lang.Unroll

import static io.micronaut.inject.test.TypeArguments.render

/**
 * The structure of the arguments the processor compiles: each shape a parameter can be written as is told apart,
 * is the same type as the same declaration compiled again, and a different type than every other shape.
 */
class ArgumentStructureSpec extends AbstractTypeElementSpec {

    private static final String PARAMETERS = '''
        List raw,
        List<?> unbounded,
        List<Object> object,
        List<? extends Number> upper,
        List<? super Integer> lower,
        List<T> recursive,
        List<S> several,
        List<U> none,
        Map<String, List<? extends Number>> nested,
        Map<Integer, String> integerToString,
        Map<String, Integer> stringToInteger,
        String[] array,
        List<String>[] arrayOfParameterized,
        List<? extends Number>[] arrayOfWildcards,
        List<T>[] arrayOfParameterizedVariable,
        T[] arrayOfRecursive,
        S[] arrayOfSeveral,
        U[] arrayOfNone,
        U[][] arrayOfArrays,
        Map<T, T> recursiveTwice,
        Map<List<T>, T[]> recursiveNestedAndArray,
        Map<T, List<T>> recursiveThenNested,
        T recursiveVariable,
        S severalVariable,
        U noneVariable,
        String type
'''

    private static final String SOURCE = """
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

interface Payment {}
interface Refundable {}

@Singleton
class Bean<T extends Comparable<T>, S extends Payment & Refundable, U> {

    @Executable
    void first($PARAMETERS) {}

    @Executable
    void second($PARAMETERS) {}
}

@Singleton
class Other<T extends Payment, S extends Refundable & Payment, U extends Comparable<U>> {

    @Executable
    void other(List<T> recursive, List<S> several, List<U> none) {}
}

@Singleton
class Linked<T extends U, U extends Comparable<U>> {

    @Executable
    void linked(T variable, List<T> list, U bound, Map<T, U> map, Map<U, T> reversed) {}
}

@Singleton
class Mutual<X extends Comparable<Y>, Y extends Comparable<X>> {

    @Executable
    void mutual(X variable, List<X> list, Y other, Map<Y, X> map) {}
}

@Singleton
class OneBound<T extends Comparable<U>, U extends Number> {

    @Executable
    void bounded(T variable, List<T> list, U bound) {}
}

@Singleton
class TwoBounds<T extends Comparable<U>, U extends Number & Runnable> {

    @Executable
    void bounded(T variable, List<T> list, U bound) {}
}
"""

    @Shared ClassLoader classLoader
    @Shared Map<String, Argument<?>> first
    @Shared Map<String, Argument<?>> second
    @Shared Map<String, Argument<?>> other
    @Shared Map<String, Argument<?>> linked
    @Shared Map<String, Argument<?>> oneBound
    @Shared Map<String, Argument<?>> mutual
    @Shared Map<String, Argument<?>> twoBounds

    def setupSpec() {
        classLoader = buildClassLoader('test.Bean', SOURCE)
        BeanDefinition<?> definition = definitionOf('Bean')
        first = arguments(definition, 'first')
        second = arguments(definition, 'second')
        other = arguments(definitionOf('Other'), 'other')
        linked = arguments(definitionOf('Linked'), 'linked')
        oneBound = arguments(definitionOf('OneBound'), 'bounded')
        mutual = arguments(definitionOf('Mutual'), 'mutual')
        twoBounds = arguments(definitionOf('TwoBounds'), 'bounded')
    }

    private BeanDefinition<?> definitionOf(String simpleName) {
        (BeanDefinition<?>) classLoader.loadClass('test.$' + simpleName + BeanDefinitionWriter.CLASS_SUFFIX).newInstance()
    }

    private static Map<String, Argument<?>> arguments(BeanDefinition<?> definition, String method) {
        definition.executableMethods.find { it.methodName == method }.arguments.collectEntries { [it.name, it] }
    }

    private static Argument<?> type(Class<?> type, Argument<?>... typeArguments) {
        Argument.of(type, (String) null, typeArguments)
    }

    private static Argument<?> upper(Argument<?> bound) {
        Argument.ofWildcard(bound.type, null, null, bound.typeParameters, [bound] as Argument[], null)
    }

    @Unroll
    void "parameter #name is compiled as #rendered: wildcard #wildcard, variable #variable, type arguments #typeArguments"() {
        given:
        Argument<?> argument = first[name]

        expect:
        render(argument) == rendered
        argument.isWildcard() == wildcard
        argument.isUnresolvedTypeVariable() == variable
        argument.hasTypeArguments() == typeArguments

        where:
        name                           | rendered                                        | wildcard | variable | typeArguments
        'raw'                          | 'List!raw<E extends Object>'                    | false    | false    | false
        'unbounded'                    | 'List<?>'                                       | false    | false    | true
        'object'                       | 'List<Object>'                                  | false    | false    | true
        'upper'                        | 'List<? extends Number>'                        | false    | false    | true
        'lower'                        | 'List<? super Integer>'                         | false    | false    | true
        'recursive'                    | 'List<T extends Comparable<T extends Comparable>>'| false    | false    | true
        'several'                      | 'List<S extends Payment & Refundable>'          | false    | false    | true
        'none'                         | 'List<U extends Object>'                        | false    | false    | true
        'nested'                       | 'Map<String, List<? extends Number>>'           | false    | false    | true
        'array'                        | 'String[]'                                      | false    | false    | false
        'arrayOfParameterized'         | 'List[]<String>'                                | false    | false    | true
        'arrayOfWildcards'             | 'List[]<? extends Number>'                      | false    | false    | true
        'arrayOfRecursive'             | '(T extends Comparable<T extends Comparable>)[]'    | false    | false    | true
        'arrayOfSeveral'               | '(S extends Payment & Refundable)[]'            | false    | false    | false
        'arrayOfNone'                  | '(U extends Object)[]'                          | false    | false    | false
        'arrayOfArrays'                | '(U extends Object)[][]'                        | false    | false    | false
        'recursiveVariable'            | 'T extends Comparable<T extends Comparable>'        | false    | true     | true
        'severalVariable'              | 'S extends Payment & Refundable'                | false    | true     | false
        'noneVariable'                 | 'U extends Object'                              | false    | true     | false
        'type'                         | 'String'                                        | false    | false    | false
    }

    void "only a compiled array of a variable is an array of an unresolved variable"() {
        expect:
        first.findAll { name, argument -> argument.isArrayOfUnresolvedTypeVariable() }.keySet() ==
            ['arrayOfRecursive', 'arrayOfSeveral', 'arrayOfNone', 'arrayOfArrays'] as Set
        first.findAll { name, argument -> argument.isUnresolvedTypeVariable() }.keySet() ==
            ['recursiveVariable', 'severalVariable', 'noneVariable'] as Set
        first.recursiveNestedAndArray.typeParameters[1].isArrayOfUnresolvedTypeVariable()
        !first.recursiveNestedAndArray.typeParameters[1].isUnresolvedTypeVariable()
        !first.unbounded.typeParameters[0].isArrayOfUnresolvedTypeVariable()
    }

    void "the type arguments are told apart too"() {
        expect:
        first.unbounded.typeParameters[0].isWildcard()
        first.upper.typeParameters[0].isWildcard()
        first.lower.typeParameters[0].isWildcard()
        first.nested.typeParameters[1].typeParameters[0].isWildcard()
        !first.unbounded.typeParameters[0].isUnresolvedTypeVariable()

        and:
        first.recursive.typeParameters[0].isUnresolvedTypeVariable()
        first.several.typeParameters[0].isUnresolvedTypeVariable()
        first.none.typeParameters[0].isUnresolvedTypeVariable()
        !first.none.typeParameters[0].isWildcard()

        and:
        !first.object.typeParameters[0].isWildcard()
        !first.object.typeParameters[0].isUnresolvedTypeVariable()
    }

    void "every parameter is the same type as the same declaration compiled again"() {
        expect:
        first.keySet() == second.keySet()
        first.every { name, argument ->
            !argument.is(second[name]) &&
                argument.equalsStructure(second[name]) &&
                second[name].equalsStructure(argument) &&
                argument.structureHashCode() == second[name].structureHashCode()
        }
    }

    void "every parameter is a different type than every other parameter"() {
        given:
        List<String> same = []
        first.each { name, argument ->
            second.each { otherName, otherArgument ->
                if (name != otherName && argument.equalsStructure(otherArgument)) {
                    same << "$name = $otherName".toString()
                }
            }
        }

        expect:
        same == []
        first.values().collect { it.structureHashCode() }.toSet().size() == first.size()
    }

    void "a variable is the same as a variable of the same name only if the bounds are the same"() {
        expect: 'the T, S and U of another class are bounded differently'
        !first.recursive.equalsStructure(other.recursive)
        !first.several.equalsStructure(other.several)
        !first.none.equalsStructure(other.none)

        and: 'which the type comparison does not see for variables that erase to the same type'
        first.several.typeParameters[0].type == other.recursive.typeParameters[0].type
    }

    void "a variable with a recursive bound is the same type wherever it is written"() {
        given:
        Argument<?> typeArgument = first.recursive.typeParameters[0]
        Argument<?> parameter = first.recursiveVariable

        expect:
        typeArgument.equalsStructure(parameter)
        parameter.equalsStructure(typeArgument)
        typeArgument.structureHashCode() == parameter.structureHashCode()

        and: 'built by hand with the bound written out once'
        Argument<?> handBuilt = Argument.ofTypeVariable(Comparable, null, 'T', null, null,
                [type(Comparable, Argument.ofTypeVariable(Comparable, null, 'T'))] as Argument[])
        typeArgument.equalsStructure(handBuilt)
        parameter.equalsStructure(handBuilt)
        parameter.structureHashCode() == handBuilt.structureHashCode()
    }

    void "a variable with a recursive bound is the variable each time it is written among the type arguments"() {
        given:
        Argument<?> variable = first.recursiveVariable

        expect: 'the second T of Map<T, T> is written the way the first is'
        render(first.recursiveTwice) == 'Map<T extends Comparable<T extends Comparable>, T extends Comparable<T extends Comparable>>'
        first.recursiveTwice.typeParameters.every { it.isUnresolvedTypeVariable() && it.equalsStructure(variable) && variable.equalsStructure(it) }
        first.recursiveTwice.typeParameters.every { it.structureHashCode() == variable.structureHashCode() }

        and: 'as is one written after a type that names the variable, or as an array'
        first.recursiveNestedAndArray.typeParameters[0].equalsStructure(first.recursive)
        first.recursiveNestedAndArray.typeParameters[1].equalsStructure(first.arrayOfRecursive)
        first.recursiveNestedAndArray.typeParameters[1].componentType().equalsStructure(variable)
        first.recursiveThenNested.typeParameters[0].equalsStructure(variable)
        first.recursiveThenNested.typeParameters[1].equalsStructure(first.recursive)
    }

    void "a variable bounded by another variable is the same type wherever it is written"() {
        given: 'T extends U, U extends Comparable<U>'
        Argument<?> variable = linked.variable
        Argument<?> bound = linked.bound

        expect:
        linked.list.typeParameters[0].equalsStructure(variable)
        variable.equalsStructure(linked.list.typeParameters[0])
        linked.list.typeParameters[0].structureHashCode() == variable.structureHashCode()

        and: 'whichever of the two variables is written first'
        linked.map.typeParameters[0].equalsStructure(variable)
        linked.map.typeParameters[1].equalsStructure(bound)
        linked.reversed.typeParameters[0].equalsStructure(bound)
        linked.reversed.typeParameters[1].equalsStructure(variable)
        linked.reversed.typeParameters[1].structureHashCode() == variable.structureHashCode()

        and: 'the bound of T is the variable U'
        ((GenericPlaceholder<?>) linked.list.typeParameters[0]).bounds[0].isUnresolvedTypeVariable()
        ((GenericPlaceholder<?>) ((GenericPlaceholder<?>) variable).bounds[0]).variableName == 'U'
    }

    void "variables that bound each other are the same type wherever they are written"() {
        expect: 'X extends Comparable<Y>, Y extends Comparable<X>'
        mutual.list.typeParameters[0].equalsStructure(mutual.variable)
        mutual.variable.equalsStructure(mutual.list.typeParameters[0])
        mutual.list.typeParameters[0].structureHashCode() == mutual.variable.structureHashCode()
        mutual.map.typeParameters[0].equalsStructure(mutual.other)
        mutual.map.typeParameters[1].equalsStructure(mutual.variable)
        mutual.map.typeParameters[1].structureHashCode() == mutual.variable.structureHashCode()
        !mutual.variable.equalsStructure(mutual.other)
    }

    void "a variable named within the bounds of another keeps its own bounds"() {
        given: 'T extends Comparable<U>, where U extends Number, and where U extends Number & Runnable'
        Argument<?> one = oneBound.list.typeParameters[0]
        Argument<?> two = twoBounds.list.typeParameters[0]

        expect: 'the U within the bound of T is the U the class declares'
        ((GenericPlaceholder<?>) two).bounds[0].typeParameters[0].equalsStructure(twoBounds.bound)
        ((GenericPlaceholder<?>) ((GenericPlaceholder<?>) two).bounds[0].typeParameters[0]).bounds*.type == [Number, Runnable]
        ((GenericPlaceholder<?>) one).bounds[0].typeParameters[0].equalsStructure(oneBound.bound)

        and: 'so the two are different types, as a type argument and as the parameter itself'
        !oneBound.list.equalsStructure(twoBounds.list)
        !twoBounds.list.equalsStructure(oneBound.list)
        !oneBound.variable.equalsStructure(twoBounds.variable)
        !oneBound.bound.equalsStructure(twoBounds.bound)

        and: 'and each is the same type wherever it is written'
        one.equalsStructure(oneBound.variable)
        two.equalsStructure(twoBounds.variable)
        two.structureHashCode() == twoBounds.variable.structureHashCode()
    }

    void "a compiled argument is the same type as one built by hand without naming the type arguments"() {
        expect:
        first.object.equalsStructure(type(List, type(Object)))
        first.upper.equalsStructure(type(List, upper(type(Number))))
        first.nested.equalsStructure(type(Map, type(String), type(List, upper(type(Number)))))
        first.arrayOfParameterized.equalsStructure(type(List[], type(String)))
        first.raw.equalsStructure(type(List))
        first.type.equalsStructure(type(String))

        and:
        !first.unbounded.equalsStructure(type(List, type(Object)))
        !first.unbounded.equalsStructure(type(List))
        !first.upper.equalsStructure(type(List, type(Number)))
        !first.integerToString.equalsStructure(type(Map, type(String), type(Integer)))
    }

    @Unroll
    void "the component of #array is the type of #component, and its array is the argument again"() {
        given:
        Argument<?> argument = first[array]
        Argument<?> expected = component instanceof String ? first[component] : component
        Argument<?> actual = argument.componentType()

        expect:
        actual != null
        actual.equalsStructure(expected)
        expected.equalsStructure(actual)
        actual.isUnresolvedTypeVariable() == expected.isUnresolvedTypeVariable()
        actual.arrayType().equalsStructure(argument)
        expected.arrayType().equalsStructure(argument)
        argument.equalsStructure(actual.arrayType())

        where:
        array                          | component
        'array'                        | 'type'
        'arrayOfParameterized'         | type(List, type(String))
        'arrayOfWildcards'             | 'upper'
        'arrayOfParameterizedVariable' | 'recursive'
        'arrayOfRecursive'             | 'recursiveVariable'
        'arrayOfSeveral'               | 'severalVariable'
        'arrayOfNone'                  | 'noneVariable'
        'arrayOfArrays'                | 'arrayOfNone'
    }

    void "the component of an array of a variable keeps the variable"() {
        given:
        Argument<?> component = first.arrayOfSeveral.componentType()

        expect:
        component instanceof GenericPlaceholder
        render(component) == 'S extends Payment & Refundable'
        render(first.arrayOfRecursive.componentType()) == 'T extends Comparable<T extends Comparable>'
        render(first.arrayOfArrays.componentType().componentType()) == 'U extends Object'
        first.arrayOfArrays.componentType().componentType().componentType() == null
    }
}
