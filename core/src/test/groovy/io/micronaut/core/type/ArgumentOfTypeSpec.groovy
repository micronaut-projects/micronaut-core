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
import io.micronaut.core.reflect.GenericTypeUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * {@link Argument#of(Type)} reads a type into the argument that keeps every shape of it, which
 * {@link Argument#asType()} renders back as an equal type.
 */
class ArgumentOfTypeSpec extends Specification {

    private static Type typeOf(String field) {
        ArgumentOfTypeFixture.getDeclaredField(field).genericType
    }

    @Unroll
    void "the type of #field renders back as the type it was read from"() {
        given:
        Type type = typeOf(field)
        Type rendered = Argument.of(type).asType()

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
        Argument.of(type).asType().typeName == type.typeName

        where:
        field << ['variable', 'selfBounded', 'plain', 'variables', 'listsOfT', 'mapsOfVariables']
    }

    void "a wildcard bounded above keeps its bound"() {
        given:
        def argument = Argument.of(typeOf('extendsNumber'))
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
        def wildcard = (WildcardArgument) Argument.of(typeOf('superInteger')).typeParameters[0]

        expect:
        wildcard.type == Integer
        wildcard.lowerBounds*.type == [Integer]
        wildcard.upperBounds*.type == [Object]
        wildcard.hasLowerBound()
    }

    void "an unbounded wildcard is bounded by Object"() {
        given:
        def wildcard = (WildcardArgument) Argument.of(typeOf('unbounded')).typeParameters[0]

        expect:
        wildcard.type == Object
        !wildcard.hasLowerBound()
    }

    void "a variable keeps every bound it declares, a bound naming the variable ending at its erasure"() {
        given:
        def variable = (GenericPlaceholder) Argument.of(typeOf('variable'))
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
        def variable = (GenericPlaceholder) Argument.of(typeOf('selfBounded'))

        expect:
        variable.type == Enum
        variable.bounds*.type == [Enum]
        variable.bounds[0].typeParameters[0].type == Enum
    }

    void "an unbounded variable is bounded by Object"() {
        given:
        def variable = (GenericPlaceholder) Argument.of(typeOf('plain'))

        expect:
        variable.type == Object
        variable.bounds*.type == [Object]
    }

    void "a generic array keeps its component"() {
        given:
        def lists = Argument.of(typeOf('listsOfT'))
        def variables = Argument.of(typeOf('variables'))

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
        Argument.of(typeOf('rawList')) == Argument.of(List)
        Argument.of(typeOf('rawList')).typeParameters.length == 0
        Argument.of(typeOf('rawLists')) == Argument.of(List[])
        Argument.of(typeOf('strings')) == Argument.of(String[])
        Argument.of(typeOf('primitive')) == Argument.INT
    }

    void "a type the JDK did not make converts the same way"() {
        given:
        Type built = GenericTypeUtils.parameterizedType(Map, String, GenericTypeUtils.parameterizedType(List, Integer))

        expect:
        Argument.of(built) == Argument.of(typeOf('nested'))
        Argument.of(built).asType() == built
    }

    void "an argument is returned unchanged, at the top and among the type arguments"() {
        given:
        def argument = Argument.of(List, String)

        expect:
        Argument.of(argument).is(argument)
        Argument.of(GenericTypeUtils.parameterizedType(List, argument)).typeParameters[0].is(argument)
    }

    void "an array argument carrying the component it was written with is returned with that component"() {
        given: "an array whose component carries the annotations of its use"
        def component = Argument.of(String, 'value', new AnnotationMetadata() {
            @Override
            boolean hasDeclaredAnnotation(String annotation) {
                annotation == 'NotBlank'
            }
        })
        def array = Argument.of(String[]).withComponentType(component)

        expect:
        array.componentType().is(component)
        Argument.of(array).is(array)
        Argument.of(array).componentType().is(component)
        Argument.of(GenericTypeUtils.parameterizedType(List, array)).typeParameters[0].componentType().is(component)
    }

    void "a nested parameterized type is owned by its declaring class, whose type arguments are not kept"() {
        given:
        Type type = typeOf('inner')
        Argument<?> argument = Argument.of(type)
        Type rendered = argument.asType()

        expect: "the type arguments of the owner are a limit of what an argument carries"
        argument.type == ArgumentOfTypeFixture.Outer.Inner
        argument.typeParameters*.type == [Integer]
        type.typeName.endsWith('ArgumentOfTypeFixture$Outer<java.lang.String>$Inner<java.lang.Integer>')
        !rendered.typeName.contains('java.lang.String')
        rendered.typeName.endsWith('Inner<java.lang.Integer>')
        rendered != type
    }

    void "an unsupported type is rejected"() {
        when:
        Argument.of(new Type() {})

        then:
        thrown(IllegalArgumentException)
    }

    void "a null type is rejected"() {
        when:
        Argument.of((Type) null)

        then:
        thrown(NullPointerException)
    }

    void "a wildcard type argument no longer makes the type raw, as it did before 5.3.0"() {
        expect:
        typeOf('unbounded').actualTypeArguments[0] instanceof WildcardType
        Argument.of(typeOf('unbounded')) != Argument.of(List)
        Argument.of(typeOf('unbounded')).typeParameters.length == 1
        Argument.of(typeOf('extendsNumber')) != Argument.of(List)
        Argument.of(typeOf('extendsNumber')) != Argument.of(List, Number)
    }

    void "a wildcard, a variable and a generic array on their own are converted rather than rejected"() {
        given:
        Type wildcard = typeOf('extendsNumber').actualTypeArguments[0]

        expect:
        Argument.of(wildcard) instanceof WildcardArgument
        Argument.of(wildcard).type == Number
        Argument.of(typeOf('variable')) instanceof GenericPlaceholder
        Argument.of(typeOf('listsOfT')).type == List[]
    }
}
