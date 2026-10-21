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

import spock.lang.Specification

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * An argument renders as the {@link Type} the declaration it describes wrote, equal to the type the JDK reflects
 * for that declaration wherever the JDK defines the equality of a type with one it did not make.
 */
class ArgumentTypesSpec extends Specification {

    private static Type typeOf(String field) {
        ArgumentTypesFixture.getDeclaredField(field).genericType
    }

    private static void sameAsJdk(Type rendered, Type reflected) {
        assert rendered == reflected
        assert reflected == rendered
        assert rendered.hashCode() == reflected.hashCode()
        assert rendered.typeName == reflected.typeName
        assert rendered.toString() == reflected.toString()
    }

    /**
     * The placeholder the processors generate for {@code T extends Number & Comparable<T>}.
     */
    private static Argument<?> selfBoundedT() {
        Argument.ofTypeVariable(Number, 'variable', 'T', null, null, [
            Argument.of(Number),
            Argument.of(Comparable, (String) null, Argument.ofTypeVariable(Number, null, 'T'))
        ] as Argument[])
    }

    void "a class renders as itself"() {
        expect:
        ArgumentTypes.toType(Argument.of(String)).is(String)
        ArgumentTypes.toType(Argument.STRING).is(String)
        ArgumentTypes.toType(Argument.OBJECT_ARGUMENT).is(Object)
    }

    void "a primitive renders as its primitive class"() {
        expect:
        ArgumentTypes.toType(Argument.INT).is(int)
        ArgumentTypes.toType(Argument.of(boolean)).is(boolean)
        ArgumentTypes.toType(Argument.VOID).is(void)
        ArgumentTypes.toType(Argument.of(int[])).is(int[])
    }

    void "an array of a class renders as the array class"() {
        expect:
        ArgumentTypes.toType(Argument.of(String[])).is(String[])
        ArgumentTypes.toType(Argument.of(String[][])).is(String[][])
        ArgumentTypes.toType(Argument.of(String).arrayType()).is(String[])
    }

    void "a raw type renders as its class"() {
        expect:
        ArgumentTypes.toType(Argument.ofRawType(List, null, null, [Argument.ofTypeVariable(Object, 'E')] as Argument[])).is(List)
    }

    void "a parameterized type is equal to the one the JDK reflects"() {
        expect:
        sameAsJdk(ArgumentTypes.toType(Argument.listOf(String)), typeOf('list'))
        sameAsJdk(ArgumentTypes.toType(Argument.mapOf(Argument.STRING, Argument.listOf(Integer))), typeOf('map'))
        ((ParameterizedType) ArgumentTypes.toType(Argument.listOf(String))).ownerType == null
        ((ParameterizedType) ArgumentTypes.toType(Argument.listOf(String))).rawType.is(List)
    }

    void "a nested parameterized type has its declaring class as its owner"() {
        given:
        def entry = ArgumentTypes.toType(Argument.of(Map.Entry, String, Integer))
        def nested = ArgumentTypes.toType(Argument.of(ArgumentTypesFixture.Nested, String))

        expect:
        ((ParameterizedType) entry).ownerType.is(Map)
        sameAsJdk(entry, typeOf('entry'))
        entry.typeName == 'java.util.Map$Entry<java.lang.String, java.lang.Integer>'
        ((ParameterizedType) nested).ownerType.is(ArgumentTypesFixture)
        sameAsJdk(nested, typeOf('nested'))
    }

    void "an inner type of a parameterized type is rendered with a class owner, and built with the parameterized one"() {
        given:
        def reflected = (ParameterizedType) typeOf('inner')
        def rendered = (ParameterizedType) ArgumentTypes.toType(Argument.of(ArgumentTypesFixture.Outer.Inner, Integer))

        expect: "an argument does not carry the type arguments of the owner"
        rendered.ownerType.is(ArgumentTypesFixture.Outer)
        rendered != reflected

        and: "the owner given is kept"
        def built = ArgumentTypes.parameterizedTypeWithOwner(
            ArgumentTypes.parameterizedType(ArgumentTypesFixture.Outer, String),
            ArgumentTypesFixture.Outer.Inner,
            Integer)
        sameAsJdk(built, reflected)
        built.typeName == 'io.micronaut.core.type.ArgumentTypesFixture$Outer<java.lang.String>$Inner<java.lang.Integer>'
    }

    void "an array of a parameterized type is a generic array"() {
        given:
        def argument = Argument.listOf(String).arrayType().arrayType()
        def rendered = ArgumentTypes.toType(argument)

        expect:
        argument.type == List[][]
        rendered instanceof GenericArrayType
        ((GenericArrayType) rendered).genericComponentType instanceof GenericArrayType
        sameAsJdk(rendered, typeOf('listMatrix'))
        rendered.typeName == 'java.util.List<java.lang.String>[][]'
    }

    void "a wildcard keeps its bounds"() {
        given:
        def extendsNumber = Argument.of(List, (String) null,
            Argument.ofWildcard(Number, 'E', null, null, [Argument.of(Number)] as Argument[], null))
        def superInteger = Argument.of(List, (String) null,
            Argument.ofWildcard(Object, 'E', null, null, null, [Argument.of(Integer)] as Argument[]))
        def unbounded = Argument.of(List, (String) null,
            Argument.ofWildcard(Object, 'E', null, null, null, null))

        expect:
        sameAsJdk(ArgumentTypes.toType(extendsNumber), typeOf('extendsNumber'))
        sameAsJdk(ArgumentTypes.toType(superInteger), typeOf('superInteger'))
        sameAsJdk(ArgumentTypes.toType(unbounded), typeOf('unbounded'))

        and:
        def wildcard = (WildcardType) ((ParameterizedType) ArgumentTypes.toType(superInteger)).actualTypeArguments[0]
        wildcard.upperBounds == [Object] as Type[]
        wildcard.lowerBounds == [Integer] as Type[]
        wildcard.typeName == '? super java.lang.Integer'
    }

    void "a type variable keeps a class bound"() {
        given:
        def rendered = (TypeVariable) ArgumentTypes.toType(Argument.ofTypeVariable(CharSequence, 'chars', 'S', null, null,
            [Argument.of(CharSequence)] as Argument[]))
        def reflected = (TypeVariable) typeOf('chars')

        expect:
        rendered.name == 'S'
        rendered.bounds == reflected.bounds
        rendered.annotatedBounds*.type == [CharSequence]
        rendered.typeName == reflected.typeName
        rendered.annotations.length == 0
    }

    void "a type variable keeps every bound, and a bound naming it names the same variable"() {
        given:
        def rendered = (TypeVariable) ArgumentTypes.toType(selfBoundedT())
        def reflected = (TypeVariable) typeOf('variable')
        def comparable = (ParameterizedType) rendered.bounds[1]

        expect:
        rendered.name == 'T'
        rendered.bounds.length == 2
        rendered.bounds[0].is(Number)
        comparable.rawType.is(Comparable)
        comparable.actualTypeArguments[0].is(rendered)
        rendered.bounds*.typeName == reflected.bounds*.typeName

        and: "two renderings are equal and the comparison ends"
        def again = ArgumentTypes.toType(selfBoundedT())
        again == rendered
        again.hashCode() == rendered.hashCode()
        ArgumentTypes.toType(Argument.listOf(selfBoundedT())) == ArgumentTypes.toType(Argument.listOf(selfBoundedT()))

        and: "a variable of the same name with other bounds is another"
        rendered != ArgumentTypes.toType(Argument.ofTypeVariable(Number, 'variable', 'T', null, null, [Argument.of(Number)] as Argument[]))
        rendered != ArgumentTypes.toType(Argument.ofTypeVariable(Number, 'variable', 'U', null, null, [Argument.of(Number), Argument.of(Comparable)] as Argument[]))
    }

    void "a rendered type variable has no declaration and is never equal to a reflected one"() {
        given:
        def rendered = (TypeVariable) ArgumentTypes.toType(selfBoundedT())
        def reflected = (TypeVariable) typeOf('variable')

        when:
        rendered.genericDeclaration

        then:
        thrown(UnsupportedOperationException)

        and: "the JDK equals a variable of its own making only"
        rendered != reflected
        reflected != rendered
        ArgumentTypes.toType(Argument.listOf(selfBoundedT())).typeName == typeOf('listOfT').typeName
        ArgumentTypes.toType(Argument.listOf(selfBoundedT())) != typeOf('listOfT')
    }

    void "an array of a type variable is a generic array of the variable"() {
        given:
        def variable = Argument.ofTypeVariable(Number, 'variables', 'T', null, null, [Argument.of(Number)] as Argument[])
        def rendered = ArgumentTypes.toType(variable.arrayType())

        expect:
        rendered instanceof GenericArrayType
        ((GenericArrayType) rendered).genericComponentType instanceof TypeVariable
        rendered.typeName == typeOf('variables').typeName
    }

    void "a resolved placeholder renders as the type resolved"() {
        expect:
        ArgumentTypes.toType(Argument.ofResolvedTypeVariable(String, 'e', 'E', null, null, null)).is(String)
        sameAsJdk(ArgumentTypes.toType(Argument.ofResolvedTypeVariable(List, 'e', 'E', null,
            [Argument.of(String)] as Argument[], null)), typeOf('list'))
    }

    void "the built types are equal to the JDK ones"() {
        expect:
        sameAsJdk(ArgumentTypes.parameterizedType(List, String), typeOf('list'))
        sameAsJdk(ArgumentTypes.parameterizedType(Map.Entry, String, Integer), typeOf('entry'))
        sameAsJdk(ArgumentTypes.parameterizedType(List, ArgumentTypes.wildcardType([Number] as Type[], [] as Type[])), typeOf('extendsNumber'))
        sameAsJdk(ArgumentTypes.parameterizedType(List, ArgumentTypes.wildcardType([] as Type[], [] as Type[])), typeOf('unbounded'))
        sameAsJdk(ArgumentTypes.genericArrayType(ArgumentTypes.genericArrayType(ArgumentTypes.parameterizedType(List, String))), typeOf('listMatrix'))
        ArgumentTypes.typeVariable('S', CharSequence).bounds == ((TypeVariable) typeOf('chars')).bounds
        ArgumentTypes.typeVariable('S').bounds == [Object] as Type[]
        ArgumentTypes.typeVariable('S', CharSequence) == ArgumentTypes.typeVariable('S', CharSequence)
    }

    void "a built type copies what it is given"() {
        given:
        Type[] arguments = [String]
        def type = ArgumentTypes.parameterizedType(List, arguments)
        arguments[0] = Integer

        expect:
        type.actualTypeArguments == [String] as Type[]
        type.actualTypeArguments.is(type.actualTypeArguments) == false
    }
}
