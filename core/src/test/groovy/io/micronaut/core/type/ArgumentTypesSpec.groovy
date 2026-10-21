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
 * {@link Argument#asType()} renders an argument as the {@link Type} the declaration it describes wrote, equal to
 * the type the JDK reflects for that declaration wherever the JDK defines the equality of a type with one it did
 * not make; the types {@link ArgumentTypes} builds are equal to the JDK ones as well.
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
        Argument.of(String).asType().is(String)
        Argument.STRING.asType().is(String)
        Argument.OBJECT_ARGUMENT.asType().is(Object)
    }

    void "a primitive renders as its primitive class"() {
        expect:
        Argument.INT.asType().is(int)
        Argument.of(boolean).asType().is(boolean)
        Argument.VOID.asType().is(void)
        Argument.of(int[]).asType().is(int[])
    }

    void "an array of a class renders as the array class"() {
        expect:
        Argument.of(String[]).asType().is(String[])
        Argument.of(String[][]).asType().is(String[][])
        Argument.of(String).arrayType().asType().is(String[])
    }

    void "a raw type renders as its class"() {
        expect:
        Argument.ofRawType(List, null, null, [Argument.ofTypeVariable(Object, 'E')] as Argument[]).asType().is(List)
    }

    void "a parameterized type is equal to the one the JDK reflects"() {
        expect:
        sameAsJdk(Argument.listOf(String).asType(), typeOf('list'))
        sameAsJdk(Argument.mapOf(Argument.STRING, Argument.listOf(Integer)).asType(), typeOf('map'))
        ((ParameterizedType) Argument.listOf(String).asType()).ownerType == null
        ((ParameterizedType) Argument.listOf(String).asType()).rawType.is(List)
    }

    void "a nested parameterized type has its declaring class as its owner"() {
        given:
        def entry = Argument.of(Map.Entry, String, Integer).asType()
        def nested = Argument.of(ArgumentTypesFixture.Nested, String).asType()

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
        def rendered = (ParameterizedType) Argument.of(ArgumentTypesFixture.Outer.Inner, Integer).asType()

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
        def rendered = argument.asType()

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
        sameAsJdk(extendsNumber.asType(), typeOf('extendsNumber'))
        sameAsJdk(superInteger.asType(), typeOf('superInteger'))
        sameAsJdk(unbounded.asType(), typeOf('unbounded'))

        and:
        def wildcard = (WildcardType) ((ParameterizedType) superInteger.asType()).actualTypeArguments[0]
        wildcard.upperBounds == [Object] as Type[]
        wildcard.lowerBounds == [Integer] as Type[]
        wildcard.typeName == '? super java.lang.Integer'
    }

    void "a type variable keeps a class bound"() {
        given:
        def rendered = (TypeVariable) Argument.ofTypeVariable(CharSequence, 'chars', 'S', null, null,
            [Argument.of(CharSequence)] as Argument[]).asType()
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
        def rendered = (TypeVariable) selfBoundedT().asType()
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
        def again = selfBoundedT().asType()
        again == rendered
        again.hashCode() == rendered.hashCode()
        Argument.listOf(selfBoundedT()).asType() == Argument.listOf(selfBoundedT()).asType()

        and: "a variable of the same name with other bounds is another"
        rendered != Argument.ofTypeVariable(Number, 'variable', 'T', null, null, [Argument.of(Number)] as Argument[]).asType()
        rendered != Argument.ofTypeVariable(Number, 'variable', 'U', null, null, [Argument.of(Number), Argument.of(Comparable)] as Argument[]).asType()
    }

    void "a rendered type variable has no declaration and is never equal to a reflected one"() {
        given:
        def rendered = (TypeVariable) selfBoundedT().asType()
        def reflected = (TypeVariable) typeOf('variable')

        when:
        rendered.genericDeclaration

        then:
        thrown(UnsupportedOperationException)

        and: "the JDK equals a variable of its own making only"
        rendered != reflected
        reflected != rendered
        Argument.listOf(selfBoundedT()).asType().typeName == typeOf('listOfT').typeName
        Argument.listOf(selfBoundedT()).asType() != typeOf('listOfT')
    }

    void "an array of a type variable is a generic array of the variable"() {
        given:
        def variable = Argument.ofTypeVariable(Number, 'variables', 'T', null, null, [Argument.of(Number)] as Argument[])
        def rendered = variable.arrayType().asType()

        expect:
        rendered instanceof GenericArrayType
        ((GenericArrayType) rendered).genericComponentType instanceof TypeVariable
        rendered.typeName == typeOf('variables').typeName
    }

    void "a resolved placeholder renders as the type resolved"() {
        expect:
        Argument.ofResolvedTypeVariable(String, 'e', 'E', null, null, null).asType().is(String)
        sameAsJdk(Argument.ofResolvedTypeVariable(List, 'e', 'E', null,
            [Argument.of(String)] as Argument[], null).asType(), typeOf('list'))
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

    void "as a parameterized type, an argument keeps its owner, its wildcards and its variables"() {
        expect:
        sameAsJdk(Argument.listOf(String).asParameterizedType(), typeOf('list'))
        sameAsJdk(Argument.of(Map.Entry, String, Integer).asParameterizedType(), typeOf('entry'))
        sameAsJdk(Argument.of(List, (String) null,
            Argument.ofWildcard(Number, 'E', null, null, [Argument.of(Number)] as Argument[], null)).asParameterizedType(),
            typeOf('extendsNumber'))
        Argument.listOf(selfBoundedT()).asParameterizedType().actualTypeArguments[0] instanceof TypeVariable
        Argument.listOf(selfBoundedT()).asParameterizedType() == Argument.listOf(selfBoundedT()).asType()
    }

    void "as a parameterized type, an argument without type arguments is its class over none"() {
        given:
        def type = Argument.of(String).asParameterizedType()

        expect:
        type.rawType.is(String)
        type.actualTypeArguments.length == 0
        type.ownerType == null
        type.typeName == 'java.lang.String'
    }

    void "as a parameterized type, an array is named the way Java writes it"() {
        expect:
        Argument.of(String[]).asParameterizedType().typeName == 'java.lang.String[]'
        Argument.of(int[][]).asParameterizedType().typeName == 'int[][]'
        Argument.of(String[]).asParameterizedType().rawType.is(String[])
        ArgumentTypes.parameterizedType(List[], String).typeName == 'java.util.List[]<java.lang.String>'
    }

    void "a type information that is not an argument renders its type arguments exactly"() {
        given:
        TypeInformation<?> information = new TypeInformation<Object>() {
            @Override
            Class<Object> getType() {
                return Map.Entry
            }

            @Override
            Argument<?>[] getTypeParameters() {
                return [Argument.of(String), Argument.ofWildcard(Number, 'V', null, null, [Argument.of(Number)] as Argument[], null)] as Argument[]
            }
        }

        expect:
        information.asType().typeName == 'java.util.Map$Entry<java.lang.String, ? extends java.lang.Number>'
        ((ParameterizedType) information.asType()).ownerType.is(Map)
        information.asParameterizedType() == information.asType()
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
