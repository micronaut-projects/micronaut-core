/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
 * @author Graeme Rocher
 * @since 1.0
 */
class ArgumentSpec extends Specification {

    private String justString
    private List<String> stringList
    private List<Integer> integerList
    private Map<String, Integer> mapStringInteger
    private Map<String, ?> mapStringWildcardInteger
    private Map<?, ?> wildcardMap
    private Map<Object, Object> objectMap
    private Map noTypeMap
    private List noTypeList
    private List<?> wildcardList
    private List<Object> objectList
    private List<Argument<?>> nestedWildcardList;
    private List<Argument<String>> nestedStringList;
    private List<Argument<Integer>> nestedIntegerList;

    @Unroll
    void 'test of parameterized type #field'() {
        given:
        def listOfString = Argument.of(getClass().getDeclaredField(field).genericType)

        expect:
        listOfString.type == type
        listOfString.typeParameters*.type == parameters

        where:
        field                | type | parameters
        "stringList"         | List | [String]
        "wildcardList"       | List | []
        "nestedWildcardList" | List | [Argument]
        "mapStringInteger"   | Map  | [String, Integer]
    }

    @Unroll
    void 'test #field isAssignableFrom from #candidateField should be #result'() {
        given:
            def argument = Argument.of(getClass().getDeclaredField(field).genericType)
            def candidateArgument = Argument.of(getClass().getDeclaredField(candidateField).genericType)

        expect:
            argument.isAssignableFrom(candidateArgument) == result

        where:
            field                      | candidateField             | result
            "noTypeList"               | "wildcardList"             | true
            "wildcardList"             | "wildcardList"             | true
            "objectList"               | "wildcardList"             | true
            "stringList"               | "integerList"              | false
            "wildcardList"             | "integerList"              | true
            "wildcardList"             | "stringList"               | true
            "objectList"               | "integerList"              | true
            "objectList"               | "stringList"               | true
            "wildcardList"             | "mapStringInteger"         | false
            "stringList"               | "wildcardList"             | false
            "integerList"              | "wildcardList"             | false
            "integerList"              | "objectList"               | false
            "nestedWildcardList"       | "nestedStringList"         | true
            "nestedWildcardList"       | "nestedIntegerList"        | true
            "nestedStringList"         | "nestedWildcardList"       | false
            "nestedIntegerList"        | "nestedWildcardList"       | false
            "noTypeList"               | "nestedWildcardList"       | true
            "wildcardList"             | "nestedWildcardList"       | true
            "wildcardList"             | "nestedWildcardList"       | true
            "wildcardList"             | "nestedStringList"         | true
            "wildcardList"             | "nestedIntegerList"        | true
            "mapStringWildcardInteger" | "mapStringInteger"         | true
            "mapStringInteger"         | "mapStringWildcardInteger" | false
            "mapStringInteger"         | "objectMap"                | false
            "wildcardMap"              | "mapStringInteger"         | true
            "wildcardMap"              | "mapStringWildcardInteger" | true
            "noTypeMap"                | "mapStringInteger"         | true
            "noTypeMap"                | "mapStringWildcardInteger" | true
            "objectMap"                | "mapStringInteger"         | true
            "objectMap"                | "mapStringWildcardInteger" | true
    }

    void "test as parameterized type"() {
        given:
        def listOfString = Argument.listOf(String)
        def parameterizedType = listOfString.asParameterizedType()

        expect:
        parameterizedType.actualTypeArguments[0].typeName == String.name
        parameterizedType.rawType.typeName == List.name
        parameterizedType.typeName == 'java.util.List<java.lang.String>'
    }

    void "test equals/hashcode"() {
        expect:
        Argument.optionalOf(Integer.class).getName() == Argument.of(Optional.class, Integer.class).getName()
        Argument.optionalOf(Integer.class).getName() == "optional"
        Argument.optionalOf(Integer.class).hashCode() == Argument.of(Optional.class, Integer.class).hashCode()
        Argument.optionalOf(Integer.class) == Argument.of(Optional.class, Integer.class)
        assertArgumentWithOneTypeParameter(Argument.of(Optional.class, Integer.class), Argument.optionalOf(Integer.class))
        assertArgumentWithOneTypeParameter(Argument.of(List.class, Integer.class), Argument.listOf(Integer.class))
        assertArgumentWithOneTypeParameter(Argument.of(Set.class, Integer.class), Argument.setOf(Integer.class))
        assertArgumentWithOneTypeParameter(Argument.of(Map.class, Integer.class, String.class), Argument.mapOf(Integer.class, String.class))
    }

    void assertArgumentWithOneTypeParameter(Argument a1, Argument a2) {
        assertArgument(a1, a2)
        assert a1.getTypeParameters() == a2.getTypeParameters()
        assertArgument(a1.getTypeParameters()[0], a2.getTypeParameters()[0])
    }

    void assertArgument(Argument a1, Argument a2) {
        assert a1 == a2
        assert a1.hashCode() == a2.hashCode()
        assert a1.name == a2.name
    }

    void "test generic list"() {
        def arg = new GenericArgument<List<String>>() {}
        expect:
        arg.getType() == List.class
        arg.getTypeParameters().length == 1
        arg.getTypeParameters()[0].getType() == String.class
        arg == Argument.listOf(String.class)
    }

    void "test generic set"() {
        def arg = new GenericArgument<Set<String>>() {}
        expect:
        arg.getType() == Set.class
        arg.getTypeParameters().length == 1
        arg.getTypeParameters()[0].getType() == String.class
        arg == Argument.setOf(String.class)
    }

    void "test generic map"() {
        def arg = new GenericArgument<Map<UUID, String>>() {}
        expect:
        arg.getType() == Map.class
        arg.getTypeParameters().length == 2
        arg.getTypeParameters()[0].getType() == UUID.class
        arg.getTypeParameters()[1].getType() == String.class
        arg == Argument.mapOf(UUID, String)
    }

    void "test generic list of lists"() {
        def arg = new GenericArgument<List<List<Long>>>() {}
        expect:
        arg.getType() == List.class
        arg.getTypeParameters()[0].getType() == List.class
        arg.getTypeParameters()[0].getTypeParameters()[0].getType() == Long.class
    }

    void "a raw argument keeps the type parameters it is given and says it is raw"() {
        given:
        def raw = Argument.ofRawType(List, 'raw', null, [Argument.ofTypeVariable(Object, 'E')] as Argument[])

        expect:
        raw.isRawType()
        raw.type == List
        raw.name == 'raw'
        raw.typeParameters*.type == [Object]

        and: 'an argument written with its type arguments is not raw, nor is a variable'
        !Argument.of(List, 'concrete', Argument.of(String, 'E')).isRawType()
        !Argument.ofTypeVariable(Object, 'E').isRawType()
        !Argument.OBJECT_ARGUMENT.isRawType()

        and: 'renaming it or giving it other metadata keeps it raw'
        raw.withName('other').isRawType()
        raw.withName('other').name == 'other'
        raw.withName('other').typeParameters*.type == [Object]
        raw.withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA).isRawType()

        and: 'a raw argument of no name is not given one by being rebuilt, as a placeholder is not'
        Argument.ofRawType(List, null, null, Argument.ZERO_ARGUMENTS)
                .withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA).toString() ==
                Argument.ofRawType(List, null, null, Argument.ZERO_ARGUMENTS).toString()

        and: 'rawness does not take part in equality, as it does not for a type variable'
        raw == Argument.of(List, 'raw', Argument.ofTypeVariable(Object, 'E'))
        raw.hashCode() == Argument.of(List, 'raw', Argument.ofTypeVariable(Object, 'E')).hashCode()
    }

    @Unroll
    void 'test #field type is equals the argument parameterized type'() {
        given:
            def argument = Argument.of(getClass().getDeclaredField(field).genericType)
            def type = getClass().getDeclaredField(field).genericType

        expect:
            argument.asType() == type
            type == argument.asType()
            type.hashCode() == argument.asType().hashCode()

        where:
            field << ["justString", "stringList", "mapStringInteger", "objectMap", "noTypeMap"]
    }

    void 'a nested wildcard is compared by the type it is bounded by in equalsType and typeHashCode'() {
        given: 'the JVM signature of a Kotlin Map<String, Any>: Map<String, ? extends Object>'
        def wildcard = Argument.ofWildcard(Object, 'V', null, null, null, null)
        def kotlinMap = Argument.of(Map, 'headers', Argument.of(String, 'K'), wildcard)
        def map = Argument.mapOf(String, Object)
        def upperBounded = Argument.of(List, 'numbers', Argument.ofWildcard(Number, 'E', null, null, [Argument.of(Number)] as Argument[], null))
        def lowerBounded = Argument.of(List, 'numbers', Argument.ofWildcard(Number, 'E', null, null, null, [Argument.of(Number)] as Argument[]))
        def nested = Argument.of(List, 'nested', Argument.of(Map, 'E', Argument.of(String, 'K'), wildcard))

        expect:
        kotlinMap.equalsType(map)
        map.equalsType(kotlinMap)
        kotlinMap.typeHashCode() == map.typeHashCode()
        !kotlinMap.equalsType(Argument.mapOf(String, String))

        and: 'whichever way the wildcard is bounded, as the processors compiled it before the bounds were kept'
        upperBounded.equalsType(Argument.listOf(Number))
        upperBounded.typeHashCode() == Argument.listOf(Number).typeHashCode()
        lowerBounded.equalsType(Argument.listOf(Number))
        lowerBounded.typeHashCode() == Argument.listOf(Number).typeHashCode()

        and: 'at any depth, a type argument being matched by the name of the type parameter it stands for'
        nested.equalsType(Argument.listOf(map.withName('E')))
        Argument.listOf(map.withName('E')).equalsType(nested)
        nested.typeHashCode() == Argument.listOf(map.withName('E')).typeHashCode()
        !nested.equalsType(Argument.listOf(Argument.mapOf(String, String).withName('E')))

        and: 'equals keeps telling a wildcard from a plain argument, in both directions'
        wildcard != Argument.of(Object, 'V')
        Argument.of(Object, 'V') != wildcard
        kotlinMap != Argument.of(Map, 'headers', Argument.of(String, 'K'), Argument.of(Object, 'V'))
        Argument.of(Map, 'headers', Argument.of(String, 'K'), Argument.of(Object, 'V')) != kotlinMap
    }

/*
    void "test inner class"() {
        def arg = new Test<String>() {}.get();
        expect:
        arg.getType() == List.class
        arg.getTypeParameters()[0].getType() == String.class
    }

    abstract class Test<T> {
        Argument<List<T>> get() {
            return new GenericArgument<List<T>>(getClass()) {}
        }
    }
*/
}
