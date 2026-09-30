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
package io.micronaut.core.type

import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The type an argument was written as: which of a type, a type variable and a wildcard it is, whether two
 * arguments were written as the same type, and the component and the array of an argument. Every argument here
 * is built by hand, the type arguments unnamed unless a case is about the names.
 */
class ArgumentStructureSpec extends Specification {

    private static Argument<?> type(Class<?> type, Argument<?>... typeArguments) {
        Argument.of(type, (String) null, typeArguments)
    }

    private static Argument<?> raw(Class<?> type, Argument<?>... declared) {
        Argument.ofRawType(type, null, null, declared)
    }

    private static Argument<?> variable(String name, Argument<?>... bounds) {
        Argument<?>[] all = bounds.length == 0 ? [Argument.OBJECT_ARGUMENT] as Argument[] : bounds
        Argument.ofTypeVariable(all[0].type, null, name, null, all[0].typeParameters, all)
    }

    private static Argument<?> resolved(Class<?> type, String name) {
        Argument.ofResolvedTypeVariable(type, name, null, null, null, null)
    }

    private static Argument<?> unbounded() {
        Argument.ofWildcard(Object, null, null, null, null, null)
    }

    private static Argument<?> upper(Argument<?> bound) {
        Argument.ofWildcard(bound.type, null, null, bound.typeParameters, [bound] as Argument[], null)
    }

    private static Argument<?> lower(Argument<?> bound) {
        Argument.ofWildcard(Object, null, null, null, null, [bound] as Argument[])
    }

    /** {@code T extends Comparable<T>}, the inner {@code T} written without its bound, as a bound names it. */
    private static Argument<?> recursive(String name) {
        variable(name, type(Comparable, Argument.ofTypeVariable(Comparable, null, name)))
    }

    @Unroll
    void "#description is told apart: wildcard #wildcard, variable #isVariable, type arguments #typeArguments"() {
        expect:
        argument.isWildcard() == wildcard
        argument.isUnresolvedTypeVariable() == isVariable
        argument.hasTypeArguments() == typeArguments

        where:
        description                  | argument                                              | wildcard | isVariable | typeArguments
        'a class'                    | type(String)                                          | false    | false      | false
        'a primitive'                | Argument.INT                                          | false    | false      | false
        'a raw type'                 | raw(List, variable('E'))                              | false    | false      | false
        'a generic class, no args'   | type(List)                                            | false    | false      | false
        'List<String>'               | type(List, type(String))                              | false    | false      | true
        'List<?>'                    | type(List, unbounded())                               | false    | false      | true
        'List<T>'                    | type(List, variable('T'))                             | false    | false      | true
        'an array'                   | type(String[])                                        | false    | false      | false
        'List<String>[]'             | type(List[], type(String))                            | false    | false      | true
        'a variable'                 | variable('T')                                         | false    | true       | false
        'a bounded variable'         | variable('T', type(Number), type(Comparable))         | false    | true       | false
        'a parameterized bound'      | variable('T', type(List, type(String)))               | false    | true       | true
        'a recursive variable'       | recursive('T')                                        | false    | true       | true
        'an array of a variable'     | Argument.ofTypeVariable(Object[], null, 'T')          | false    | false      | false
        'a resolved variable'        | resolved(Integer, 'T')                                | false    | false      | false
        'an unbounded wildcard'      | unbounded()                                           | true     | false      | false
        'an upper bounded wildcard'  | upper(type(Number))                                   | true     | false      | false
        'a lower bounded wildcard'   | lower(type(Integer))                                  | true     | false      | false
        'a wildcard of an array'     | upper(type(String[]))                                 | true     | false      | false
    }

    void "a wildcard and a resolved variable are still placeholders, which is why the shape is asked separately"() {
        expect:
        unbounded() instanceof GenericPlaceholder
        unbounded().isTypeVariable()
        resolved(Integer, 'T') instanceof GenericPlaceholder
        resolved(Integer, 'T').isTypeVariable()
    }

    @Unroll
    void "#description has the same structure as itself built again"() {
        given:
        Argument<?> one = build.call()
        Argument<?> other = build.call()

        expect:
        !one.is(other)
        one.equalsStructure(one)
        one.equalsStructure(other)
        other.equalsStructure(one)
        one.structureHashCode() == other.structureHashCode()
        !one.equalsStructure(null)

        where:
        description                   | build
        'a class'                     | { type(String) }
        'a raw type'                  | { raw(List, variable('E')) }
        'List<String>'                | { type(List, type(String)) }
        'List<?>'                     | { type(List, unbounded()) }
        'List<? extends Number>'      | { type(List, upper(type(Number))) }
        'List<? super Integer>'       | { type(List, lower(type(Integer))) }
        'Map<String, List<? extends Number>>' | { type(Map, type(String), type(List, upper(type(Number)))) }
        'a variable'                  | { variable('T') }
        'a variable of two bounds'    | { variable('T', type(Number), type(Comparable)) }
        'a recursive variable'        | { recursive('T') }
        'List<T>'                     | { type(List, variable('T', type(Number))) }
        'List<String>[]'              | { type(List[], type(String)) }
        'T[]'                         | { Argument.ofTypeVariable(Number[], null, 'T', null, null, [type(Number)] as Argument[]) }
        'T[][]'                       | { Argument.ofTypeVariable(Number[][], null, 'T') }
    }

    @Unroll
    void "#description: #one and #other are different types"() {
        expect:
        !one.equalsStructure(other)
        !other.equalsStructure(one)

        where:
        description                                  | one                                          | other
        'different classes'                          | type(String)                                 | type(Integer)
        'a raw type and one of wildcards'            | raw(List, variable('E'))                     | type(List, unbounded())
        'no type arguments and wildcards'            | type(List)                                   | type(List, unbounded())
        'a raw type and one of Object'               | raw(List, variable('E'))                     | type(List, type(Object))
        'a wildcard and Object'                      | type(List, unbounded())                      | type(List, type(Object))
        'a wildcard and its bound'                   | type(List, upper(type(Number)))              | type(List, type(Number))
        'an upper and a lower bound'                 | type(List, upper(type(Number)))              | type(List, lower(type(Number)))
        'different upper bounds'                     | upper(type(Number))                          | upper(type(Integer))
        'different lower bounds'                     | lower(type(Number))                          | lower(type(Integer))
        'a bounded and an unbounded wildcard'        | upper(type(Number))                          | unbounded()
        'a lower bounded and an unbounded wildcard'  | lower(type(Number))                          | unbounded()
        'a wildcard and a variable'                  | unbounded()                                  | variable('T')
        'a wildcard and a type'                      | unbounded()                                  | type(Object)
        'variables of different names'               | variable('T')                                | variable('S')
        'variables of different bounds'              | variable('T', type(Number))                  | variable('T', type(Integer))
        'a variable of one bound and of two'         | variable('T', type(Number))                  | variable('T', type(Number), type(Comparable))
        'bounds in another order'                    | variable('T', type(Comparable), type(Number)) | variable('T', type(Number), type(Comparable))
        'bounds of different type arguments'         | variable('T', type(List, type(String)))      | variable('T', type(List, type(Integer)))
        'recursive variables of different names'     | recursive('T')                               | recursive('S')
        'a variable and the type it erases to'       | variable('T', type(Number))                  | type(Number)
        'a variable and a type resolved in place'    | variable('T', type(Number))                  | resolved(Number, 'T')
        'a variable and an array of it'              | variable('T')                                | Argument.ofTypeVariable(Object[], null, 'T')
        'an array of a variable and of its erasure'  | Argument.ofTypeVariable(Object[], null, 'T') | type(Object[])
        'arrays of different type arguments'         | type(List[], type(String))                   | type(List[], type(Integer))
        'an array and its component'                 | type(List[], type(String))                   | type(List, type(String))
        'nested type arguments that differ'          | type(Map, type(String), type(List, type(Integer))) | type(Map, type(String), type(List, type(Long)))
        'nested wildcards that differ'               | type(List, type(List, upper(type(Number))))  | type(List, type(List, lower(type(Number))))
        'different numbers of type arguments'        | type(Map, type(String))                      | type(Map, type(String), type(String))
    }

    void "a raw type and a type with no type arguments are the same type"() {
        expect:
        raw(List, variable('E')).equalsStructure(type(List))
        type(List).equalsStructure(raw(List, variable('E')))
        raw(List, variable('E')).structureHashCode() == type(List).structureHashCode()
    }

    void "an unbounded wildcard and one bounded by Object are the same type"() {
        expect:
        unbounded().equalsStructure(upper(type(Object)))
        upper(type(Object)).equalsStructure(unbounded())
        unbounded().structureHashCode() == upper(type(Object)).structureHashCode()
    }

    void "a type resolved in place of a variable is the type it was resolved to"() {
        expect:
        resolved(Integer, 'T').equalsStructure(type(Integer))
        type(Integer).equalsStructure(resolved(Integer, 'T'))
        resolved(Integer, 'T').structureHashCode() == type(Integer).structureHashCode()
        type(List, resolved(Integer, 'E')).equalsStructure(type(List, type(Integer)))
    }

    void "a variable whose bounds were not recorded is bounded by the type it erases to"() {
        expect:
        Argument.ofTypeVariable(Number, null, 'T').equalsStructure(variable('T', type(Number)))
        Argument.ofTypeVariable(Number, null, 'T').structureHashCode() == variable('T', type(Number)).structureHashCode()
        Argument.ofTypeVariable(Object, null, 'T').equalsStructure(variable('T'))
    }

    void "a variable named within its own bounds is the variable, however deep the bound was written out"() {
        given:
        Argument<?> once = recursive('T')
        Argument<?> twice = variable('T', type(Comparable, recursive('T')))

        expect:
        once.equalsStructure(twice)
        twice.equalsStructure(once)
        once.structureHashCode() == twice.structureHashCode()

        and: 'another variable within the bounds is still compared by its own bounds'
        !variable('T', type(Comparable, variable('S', type(Number)))).equalsStructure(
                variable('T', type(Comparable, variable('S', type(Integer)))))
    }

    void "the name and the annotations of an argument, and the names of its type arguments, are not compared"() {
        given:
        AnnotationMetadata annotated = new AnnotationMetadata() {
            @Override boolean hasAnnotation(String annotation) { true }
        }
        Argument<?> named = Argument.of(Map, 'headers', annotated, Argument.of(String, 'K'), Argument.of(Integer, 'V'))
        Argument<?> unnamed = type(Map, type(String), type(Integer))

        expect:
        named != unnamed
        named.equalsStructure(unnamed)
        unnamed.equalsStructure(named)
        named.structureHashCode() == unnamed.structureHashCode()

        and: 'a variable is the same whatever its argument is called, and a wildcard whatever parameter it stands for'
        Argument.ofTypeVariable(Number, 'value', 'T').equalsStructure(Argument.ofTypeVariable(Number, 'other', 'T'))
        Argument.ofWildcard(Object, 'E', null, null, null, null).equalsStructure(Argument.ofWildcard(Object, 'T', annotated, null, null, null))
    }

    void "type arguments are compared by position, so they need no names"() {
        given: 'type arguments named after their own types, as an argument built without names is'
        Argument<?> stringToInteger = type(Map, type(String), type(Integer))
        Argument<?> integerToString = type(Map, type(Integer), type(String))

        expect: 'the structure tells apart what the names alone do not'
        stringToInteger.typeVariables.keySet() == integerToString.typeVariables.keySet()
        !stringToInteger.equalsStructure(integerToString)

        and: 'and two type arguments of the same type, which share a name, are both compared'
        type(Map, type(String), type(String)).typeVariables.size() == 1
        !type(Map, type(List, type(String)), type(List, type(Integer))).equalsStructure(
                type(Map, type(List, type(String)), type(List, type(String))))
        type(Map, type(String), type(String)).hasTypeArguments()

        and: 'differently named type arguments of the same types are the same type'
        Argument.mapOf(String, Integer).equalsStructure(stringToInteger)
        Argument.listOf(String).equalsStructure(Argument.of(List, String))
    }

    @Unroll
    void "the component of #description is #rendered, and its array is the argument again"() {
        when:
        Argument<?> component = array.componentType()

        then:
        component != null
        component.type == array.type.componentType
        component.equalsStructure(expected)
        component.isUnresolvedTypeVariable() == expected.isUnresolvedTypeVariable()
        component.isRawType() == expected.isRawType()
        component.annotationMetadata.isEmpty()

        and:
        component.arrayType().equalsStructure(array)
        component.arrayType().type == array.type
        component.arrayType().isRawType() == array.isRawType()
        expected.arrayType().equalsStructure(array)

        where:
        description                 | array                                                                          | expected
        'String[]'                  | type(String[])                                                                 | type(String)
        'int[]'                     | type(int[])                                                                    | Argument.INT
        'String[][]'                | type(String[][])                                                               | type(String[])
        'List<String>[]'            | type(List[], type(String))                                                     | type(List, type(String))
        'List<? extends Number>[]'  | type(List[], upper(type(Number)))                                              | type(List, upper(type(Number)))
        'Map<String, List<T>>[]'    | type(Map[], type(String), type(List, variable('T')))                           | type(Map, type(String), type(List, variable('T')))
        'a raw List[]'              | raw(List[], variable('E'))                                                     | raw(List, variable('E'))
        'T[]'                       | Argument.ofTypeVariable(Object[], null, 'T')                                   | variable('T')
        'T[] of a bounded T'        | Argument.ofTypeVariable(Number[], null, 'T', null, null, [type(Number), type(Comparable)] as Argument[]) | variable('T', type(Number), type(Comparable))
        'T[] of a parameterized T'  | Argument.ofTypeVariable(List[], null, 'T', null, [type(String)] as Argument[], [type(List, type(String))] as Argument[]) | variable('T', type(List, type(String)))
        'T[][]'                     | Argument.ofTypeVariable(Object[][], null, 'T')                                 | Argument.ofTypeVariable(Object[], null, 'T')
        'a resolved T of String[]'  | resolved(String[], 'T')                                                        | type(String)
        rendered = expected.toString()
    }

    void "the component of an array of a variable is that variable, with its name and bounds"() {
        given:
        Argument<?> array = Argument.ofTypeVariable(Number[], 'values', 'T', null, null, [type(Number), type(Comparable)] as Argument[])

        when:
        Argument<?> component = array.componentType()

        then:
        component instanceof GenericPlaceholder
        component.isUnresolvedTypeVariable()
        ((GenericPlaceholder<?>) component).variableName == 'T'
        ((GenericPlaceholder<?>) component).bounds*.type == [Number, Comparable]
        component.type == Number

        and: 'the array of a variable is a placeholder of the array of what the variable erases to'
        Argument<?> again = component.arrayType()
        again instanceof GenericPlaceholder
        !again.isUnresolvedTypeVariable()
        again.type == Number[]
        ((GenericPlaceholder<?>) again).variableName == 'T'
        ((GenericPlaceholder<?>) again).bounds*.type == [Number, Comparable]
    }

    void "an argument that is not an array has no component"() {
        expect:
        type(String).componentType() == null
        type(List, type(String)).componentType() == null
        variable('T').componentType() == null
        unbounded().componentType() == null
    }

    void "a wildcard is neither an array nor the component of one"() {
        given:
        Argument<?> wildcard = upper(type(String[]))

        expect: 'a wildcard bounded by an array is a wildcard'
        wildcard.isArray()
        wildcard.componentType() == null

        when:
        wildcard.arrayType()

        then:
        thrown(IllegalStateException)
    }

    void "void has no array"() {
        when:
        Argument.VOID.arrayType()

        then:
        thrown(IllegalStateException)
    }

    void "an argument that is not the default implementation is compared by what it answers"() {
        given:
        Argument<?> delegate = type(List, type(String))
        Argument<?> custom = new Argument<List>() {
            @Override String getName() { 'custom' }
            @Override boolean equalsType(Argument<?> other) { delegate.equalsType(other) }
            @Override int typeHashCode() { delegate.typeHashCode() }
            @Override Class<List> getType() { List }
            @Override Argument<?>[] getTypeParameters() { delegate.typeParameters }
            @Override Map<String, Argument<?>> getTypeVariables() { [:] }
        }

        expect:
        custom.equalsStructure(delegate)
        delegate.equalsStructure(custom)
        custom.structureHashCode() == delegate.structureHashCode()
        !custom.isWildcard()
        !custom.isUnresolvedTypeVariable()
        custom.hasTypeArguments()
        custom.arrayType().equalsStructure(type(List[], type(String)))
    }
}
