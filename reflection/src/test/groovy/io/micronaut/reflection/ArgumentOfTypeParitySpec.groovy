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
package io.micronaut.reflection

import io.micronaut.core.type.Argument
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The suite of {@code io.micronaut.core.type.ArgumentSpec} that exercises {@link Argument#of(java.lang.reflect.Type)},
 * asked of {@link ReflectionArguments#of(java.lang.reflect.Type)} instead.
 *
 * <p>The two agree on every type {@code Argument.of} accepts, save for a wildcard: {@code Argument.of} drops the
 * type arguments of the type that contains one, where {@link ReflectionArguments} resolves it to its bound, the
 * way the annotation processors record it. The cases below say which is which.</p>
 */
class ArgumentOfTypeParitySpec extends Specification {

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
        def argument = ReflectionArguments.of(getClass().getDeclaredField(field).genericType)

        expect:
        argument.type == type
        argument.typeParameters*.type == parameters
        argument.typeParameters*.type == Argument.of(getClass().getDeclaredField(field).genericType).typeParameters*.type

        where:
        field                | type | parameters
        "justString"         | String | []
        "stringList"         | List | [String]
        "noTypeList"         | List | []
        "mapStringInteger"   | Map  | [String, Integer]
        "objectMap"          | Map  | [Object, Object]
        "noTypeMap"          | Map  | []
    }

    @Unroll
    void 'test of parameterized type #field with a wildcard'() {
        given:
        def type = getClass().getDeclaredField(field).genericType

        expect: "the wildcard is resolved to its bound"
        ReflectionArguments.of(type).type == raw
        ReflectionArguments.of(type).typeParameters*.type == parameters

        and: "where Argument.of drops the type arguments of the containing type"
        Argument.of(type).type == raw
        Argument.of(type).typeParameters*.type == erased

        where:
        field                      | raw  | parameters         | erased
        "wildcardList"             | List | [Object]           | []
        "wildcardMap"              | Map  | [Object, Object]   | []
        "mapStringWildcardInteger" | Map  | [String, Object]   | []
        "nestedWildcardList"       | List | [Argument]         | [Argument]
    }

    void 'a nested wildcard is resolved to its bound too'() {
        given:
        def type = getClass().getDeclaredField("nestedWildcardList").genericType

        expect:
        ReflectionArguments.of(type).typeParameters[0].typeParameters*.type == [Object]
        Argument.of(type).typeParameters[0].typeParameters*.type == []
    }

    @Unroll
    void 'test #field isAssignableFrom from #candidateField should be #result'() {
        given:
            def argument = ReflectionArguments.of(getClass().getDeclaredField(field).genericType)
            def candidateArgument = ReflectionArguments.of(getClass().getDeclaredField(candidateField).genericType)

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

    @Unroll
    void 'test #field type is equals the argument parameterized type'() {
        given:
            def type = getClass().getDeclaredField(field).genericType
            def rendered = ReflectionArguments.toType(ReflectionArguments.of(type))

        expect:
            rendered == type
            type == rendered
            type.hashCode() == rendered.hashCode()

        where:
            field << ["justString", "stringList", "mapStringInteger", "objectMap", "noTypeMap"]
    }

    private List<? extends CharSequence> withWildcard

    void 'a wildcard and a generic array are types Argument.of rejects'() {
        given:
        def wildcard = ((java.lang.reflect.ParameterizedType) ArgumentOfTypeParitySpec.getDeclaredField("withWildcard").genericType)
                .actualTypeArguments[0]
        def genericArray = Holder.getDeclaredMethod("array").genericReturnType

        when:
        Argument.of(wildcard)

        then:
        thrown(IllegalArgumentException)

        when:
        Argument.of(genericArray)

        then:
        thrown(IllegalArgumentException)

        expect:
        ReflectionArguments.of(wildcard).type == CharSequence
        ReflectionArguments.of(genericArray).type == String[]
    }

    static class Holder<T extends String> {
        T[] array() {
            return null
        }
    }
}
