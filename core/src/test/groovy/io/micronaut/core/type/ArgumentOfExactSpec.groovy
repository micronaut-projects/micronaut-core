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

import io.micronaut.core.reflect.GenericTypeUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * {@link Argument#ofExact(Type)} reads a type into the argument that keeps every shape of it, which
 * {@link Argument#asType()} renders back as an equal type.
 */
class ArgumentOfExactSpec extends Specification {

    private static Type typeOf(String field) {
        ArgumentOfExactFixture.getDeclaredField(field).genericType
    }

    @Unroll
    void "the type of #field renders back as the type it was read from"() {
        given:
        Type type = typeOf(field)
        Type rendered = Argument.ofExact(type).asType()

        expect:
        rendered == type
        type == rendered
        rendered.typeName == type.typeName

        where:
        field << ['extendsNumber', 'superInteger', 'unbounded', 'nestedWildcard', 'nested', 'listMatrix',
                  'rawList', 'rawLists', 'strings', 'primitive']
    }

    @Unroll
    void "the type of #field naming a variable renders back as a type of the same name"() {
        given: "the JDK compares a variable by its declaration, which a type rendered elsewhere does not have"
        Type type = typeOf(field)

        expect:
        Argument.ofExact(type).asType().typeName == type.typeName

        where:
        field << ['variable', 'selfBounded', 'plain', 'variables', 'listsOfT', 'mapsOfVariables']
    }

    void "a wildcard bounded above keeps its bound"() {
        given:
        def argument = Argument.ofExact(typeOf('extendsNumber'))
        def wildcard = (WildcardArgument) argument.typeParameters[0]

        expect:
        argument.type == List
        wildcard.type == Number
        wildcard.upperBounds*.type == [Number]
        wildcard.lowerBounds.isEmpty()
        argument != Argument.of(List, Number)
    }

    void "a wildcard bounded below is of its lower bound, as the processors build it, and keeps both bounds"() {
        given:
        def wildcard = (WildcardArgument) Argument.ofExact(typeOf('superInteger')).typeParameters[0]

        expect:
        wildcard.type == Integer
        wildcard.lowerBounds*.type == [Integer]
        wildcard.upperBounds*.type == [Object]
        wildcard.hasLowerBound()
    }

    void "an unbounded wildcard is bounded by Object"() {
        given:
        def wildcard = (WildcardArgument) Argument.ofExact(typeOf('unbounded')).typeParameters[0]

        expect:
        wildcard.type == Object
        !wildcard.hasLowerBound()
    }

    void "a variable keeps every bound it declares, a bound naming the variable ending at its erasure"() {
        given:
        def variable = (GenericPlaceholder) Argument.ofExact(typeOf('variable'))
        def comparable = variable.bounds[1]

        expect:
        variable.variableName == 'T'
        variable.type == Number
        variable.bounds*.type == [Number, Comparable]
        comparable.typeParameters[0] instanceof GenericPlaceholder
        ((GenericPlaceholder) comparable.typeParameters[0]).variableName == 'T'
        comparable.typeParameters[0].type == Number
    }

    void "a variable bounded by a type naming it erases to that type"() {
        given:
        def variable = (GenericPlaceholder) Argument.ofExact(typeOf('selfBounded'))

        expect:
        variable.type == Enum
        variable.bounds*.type == [Enum]
        variable.bounds[0].typeParameters[0].type == Enum
    }

    void "an unbounded variable is bounded by Object"() {
        given:
        def variable = (GenericPlaceholder) Argument.ofExact(typeOf('plain'))

        expect:
        variable.type == Object
        variable.bounds*.type == [Object]
    }

    void "a generic array keeps its component"() {
        given:
        def lists = Argument.ofExact(typeOf('listsOfT'))
        def variables = Argument.ofExact(typeOf('variables'))

        expect:
        typeOf('listsOfT') instanceof GenericArrayType
        lists.type == List[]
        lists.componentType().type == List
        lists.componentType().typeParameters[0] instanceof GenericPlaceholder
        variables instanceof GenericPlaceholder
        variables.type == Number[]
        variables.componentType() instanceof GenericPlaceholder
    }

    void "a class is the argument of that class, a generic one carrying no type arguments"() {
        expect: "the variables a class declares are read from the class, which the conversion does not do"
        Argument.ofExact(typeOf('rawList')) == Argument.of(List)
        Argument.ofExact(typeOf('rawList')).typeParameters.length == 0
        Argument.ofExact(typeOf('rawLists')) == Argument.of(List[])
        Argument.ofExact(typeOf('strings')) == Argument.of(String[])
        Argument.ofExact(typeOf('primitive')) == Argument.INT
    }

    void "a type the JDK did not make converts the same way"() {
        given:
        Type built = GenericTypeUtils.parameterizedType(Map, String, GenericTypeUtils.parameterizedType(List, Integer))

        expect:
        Argument.ofExact(built) == Argument.ofExact(typeOf('nested'))
        Argument.ofExact(built).asType() == built
    }

    void "an argument is returned as it is, or renamed"() {
        given:
        def argument = Argument.of(List, String)

        expect:
        Argument.ofExact(argument).is(argument)
        Argument.ofExact('items', argument).name == 'items'
        Argument.ofExact('items', argument).type == List
        Argument.ofExact('items', argument).typeParameters == argument.typeParameters
        Argument.ofExact(GenericTypeUtils.parameterizedType(List, argument)).typeParameters[0] == argument
    }

    void "a named conversion names the argument"() {
        expect:
        Argument.ofExact('values', typeOf('extendsNumber')).name == 'values'
    }

    void "an unsupported type is rejected"() {
        when:
        Argument.ofExact(new Type() {})

        then:
        thrown(IllegalArgumentException)
    }

    void "a null type is rejected"() {
        when:
        Argument.ofExact((Type) null)

        then:
        thrown(NullPointerException)
    }

    void "Argument.of(Type) still reads a wildcard type argument as making the type raw"() {
        expect:
        Argument.of(typeOf('extendsNumber')) == Argument.of(List)
        !(typeOf('extendsNumber').actualTypeArguments[0] instanceof Argument)
        typeOf('extendsNumber').actualTypeArguments[0] instanceof WildcardType
    }
}
