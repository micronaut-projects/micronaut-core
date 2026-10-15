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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.python.annotation.processing.test.generics.Book
import spock.lang.Shared
import spock.lang.Unroll

import static io.micronaut.inject.test.TypeArguments.render

/**
 * The structure of the arguments the Python processor compiles: of the Java signatures a Python bean inherits, and
 * of the generics a Python class declares itself. Python type hints have neither a wildcard nor an array, so those
 * come from the Java contract.
 */
class ArgumentStructureSpec extends AbstractPythonTypeElementSpec {

    private static final String BEANS = '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import StructureContract, Book

U = TypeVar("U")

@Singleton
class Open(StructureContract[str, U], Generic[U]):
    def name(self) -> str:
        return "open"

@Singleton
class Bound(StructureContract[str, Book]):
    def name(self) -> str:
        return "bound"
'''

    private static final String CATALOG = '''
from typing import Generic, TypeVar
from dataclasses import dataclass
from io.micronaut.core.annotation import Introspected
from io.micronaut.python.annotation.processing.test.generics import Book

V = TypeVar("V", bound=Book)
W = TypeVar("W")

@Introspected
@dataclass
class Catalog(Generic[V]):
    prices: list[float]
    items: list[V]
    index: dict[str, V]
    item: V

@Introspected
@dataclass
class Unbounded(Generic[W]):
    items: list[W]
    item: W
'''

    /** The parameters that are each written as a different type. */
    private static final List<String> DISTINCT = ['raw', 'unbounded', 'object', 'upper', 'lower', 'recursive', 'none',
                                                  'nested', 'recursiveTwice', 'array', 'arrayOfParameterized',
                                                  'noneVariable', 'type']

    @Shared Map<String, Argument<?>> first
    @Shared Map<String, Argument<?>> second
    @Shared Map<String, Argument<?>> bound
    @Shared Map<String, Argument<?>> catalog
    @Shared Map<String, Argument<?>> unbounded

    def setupSpec() {
        BeanDefinition<?> open = buildBeanDefinition('python', 'Open', BEANS)
        first = arguments(open, 'first')
        second = arguments(open, 'second')
        bound = arguments(buildBeanDefinition('python', 'Bound', BEANS), 'first')
        catalog = properties(buildBeanIntrospection('python.Catalog', CATALOG))
        unbounded = properties(buildBeanIntrospection('python.Unbounded', CATALOG))
    }

    private static Map<String, Argument<?>> arguments(BeanDefinition<?> definition, String method) {
        definition.executableMethods.find { it.methodName == method }.arguments.collectEntries { [it.name, it] }
    }

    private static Map<String, Argument<?>> properties(BeanIntrospection<?> introspection) {
        introspection.beanProperties.collectEntries { [it.name, it.asArgument()] }
    }

    private static Argument<?> type(Class<?> type, Argument<?>... typeArguments) {
        Argument.of(type, (String) null, typeArguments)
    }

    private static Argument<?> upper(Argument<?> bound) {
        Argument.ofWildcard(bound.type, null, null, bound.typeParameters, [bound] as Argument[], null)
    }

    @Unroll
    void "the inherited parameter #name is compiled as #rendered: variable #variable, type arguments #typeArguments"() {
        given:
        Argument<?> argument = first[name]

        expect:
        render(argument) == rendered
        !argument.isWildcard()
        argument.isUnresolvedTypeVariable() == variable
        argument.hasTypeArguments() == typeArguments

        where:
        name                   | rendered                              | variable | typeArguments
        'raw'                  | 'List!raw<E extends Object>'          | false    | false
        'unbounded'            | 'List<?>'                             | false    | true
        'object'               | 'List<Object>'                        | false    | true
        'upper'                | 'List<? extends Number>'              | false    | true
        'lower'                | 'List<? super Integer>'               | false    | true
        'recursive'            | 'List<String>'                        | false    | true
        'none'                 | 'List<U extends Object>'              | false    | true
        'nested'               | 'Map<String, List<? extends Number>>' | false    | true
        'recursiveTwice'       | 'Map<String, String>'                 | false    | true
        'array'                | 'String[]'                            | false    | false
        'arrayOfParameterized' | 'List[]<String>'                      | false    | true
        'noneVariable'         | 'U extends Object'                    | true     | false
        'type'                 | 'String'                              | false    | false
    }

    void "the type arguments of an inherited parameter are told apart"() {
        expect:
        first.unbounded.typeParameters[0].isWildcard()
        first.upper.typeParameters[0].isWildcard()
        first.lower.typeParameters[0].isWildcard()
        first.nested.typeParameters[1].typeParameters[0].isWildcard()
        !first.unbounded.typeParameters[0].isUnresolvedTypeVariable()

        and: 'the variable the Python class leaves open'
        first.none.typeParameters[0].isUnresolvedTypeVariable()
        !first.none.typeParameters[0].isWildcard()

        and: 'and the types the Python class binds'
        !first.recursive.typeParameters[0].isWildcard()
        !first.recursive.typeParameters[0].isUnresolvedTypeVariable()
        !bound.none.typeParameters[0].isUnresolvedTypeVariable()
        bound.none.typeParameters[0].type == Book
    }

    void "every inherited parameter is the same type as the same declaration compiled again"() {
        expect:
        first.keySet() == second.keySet()
        first.every { name, argument ->
            !argument.is(second[name]) &&
                argument.equalsStructure(second[name]) &&
                second[name].equalsStructure(argument) &&
                argument.structureHashCode() == second[name].structureHashCode()
        }
    }

    void "every inherited parameter is a different type than every other one"() {
        given:
        List<String> same = []
        DISTINCT.each { name ->
            DISTINCT.each { otherName ->
                if (name != otherName && first[name].equalsStructure(second[otherName])) {
                    same << "$name = $otherName".toString()
                }
            }
        }

        expect:
        same == []
        DISTINCT.collect { first[it].structureHashCode() }.toSet().size() == DISTINCT.size()
    }

    void "an inherited parameter is the same type as one built by hand without naming the type arguments"() {
        expect:
        first.object.equalsStructure(type(List, type(Object)))
        first.upper.equalsStructure(type(List, upper(type(Number))))
        first.nested.equalsStructure(type(Map, type(String), type(List, upper(type(Number)))))
        first.recursive.equalsStructure(type(List, type(String)))
        first.recursiveTwice.equalsStructure(type(Map, type(String), type(String)))
        first.arrayOfParameterized.equalsStructure(type(List[], type(String)))
        first.raw.equalsStructure(type(List))
        bound.none.equalsStructure(type(List, type(Book)))

        and:
        !first.unbounded.equalsStructure(type(List, type(Object)))
        !first.unbounded.equalsStructure(type(List))
        !first.upper.equalsStructure(type(List, type(Number)))
    }

    void "a variable the Python class leaves open is not the type another class binds it to"() {
        expect:
        !first.none.equalsStructure(bound.none)
        !first.noneVariable.equalsStructure(bound.noneVariable)
        bound.noneVariable.equalsStructure(type(Book))
    }

    void "the component of an inherited array keeps its type arguments, and its array is the argument again"() {
        expect:
        first.array.componentType().equalsStructure(first.type)
        first.type.arrayType().equalsStructure(first.array)
        first.arrayOfParameterized.componentType().equalsStructure(first.recursive)
        first.recursive.arrayType().equalsStructure(first.arrayOfParameterized)
        first.type.componentType() == null
    }

    @Unroll
    void "the Python property #name is compiled as #rendered"() {
        expect:
        render(catalog[name]) == rendered
        catalog[name].isUnresolvedTypeVariable() == variable
        catalog[name].hasTypeArguments() == typeArguments

        where:
        name     | rendered                       | variable | typeArguments
        'prices' | 'List<Double>'                 | false    | true
        'items'  | 'List<V extends Book>'         | false    | true
        'index'  | 'Map<String, V extends Book>'  | false    | true
        'item'   | 'V extends Book'               | true     | false
    }

    void "a variable a Python class declares is the same type wherever it is written"() {
        given:
        Argument<?> variable = catalog.item

        expect:
        variable instanceof GenericPlaceholder
        catalog.items.typeParameters[0].isUnresolvedTypeVariable()
        catalog.items.typeParameters[0].equalsStructure(variable)
        catalog.index.typeParameters[1].equalsStructure(variable)
        catalog.items.typeParameters[0].structureHashCode() == variable.structureHashCode()

        and: 'and is neither the type it erases to nor a variable of another bound'
        !variable.equalsStructure(type(Book))
        !catalog.items.equalsStructure(type(List, type(Book)))
        !catalog.items.equalsStructure(unbounded.items)
        !variable.equalsStructure(unbounded.item)

        and: 'while the type comparison takes the variable for the type it erases to'
        catalog.items.equalsType(Argument.listOf(Book))
    }

    void "the array of a Python variable is an array of that variable"() {
        given:
        Argument<?> array = catalog.item.arrayType()

        expect:
        array.type == Book[]
        !array.isUnresolvedTypeVariable()
        array.componentType().isUnresolvedTypeVariable()
        array.componentType().equalsStructure(catalog.item)
        catalog.items.arrayType().componentType().equalsStructure(catalog.items)
    }
}
