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

import io.micronaut.core.reflect.GenericTypeUtils
import spock.lang.Specification

import java.util.function.Supplier

/**
 * The suite of {@link GenericTypeUtils}, asked of {@link ReflectionArguments} instead. {@code GenericTypeUtils}
 * answers in erasure - a raw {@code Class[]} - where {@link ReflectionArguments#resolveGenericToArgument} answers
 * with an {@link io.micronaut.core.type.Argument} that carries the nested type arguments and the annotations, so
 * each case asserts the raw answer both ways and then what the argument adds.
 */
class GenericTypeUtilsParitySpec extends Specification {

    void "test resolve generic super type"() {
        given:
        def argument = ReflectionArguments.resolveGenericToArgument(Baz, Bar)

        expect:
        argument.type == Bar
        argument.typeParameters*.type == [String]
        argument.typeParameters*.type as Class[] == GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Bar)
    }

    static class Foo<T> {}

    static class Bar<T> extends Foo<T> {}

    static class Baz extends Bar<String> {}

    // =======================

    // https://github.com/micronaut-projects/micronaut-openapi/issues/238
    void "test resolveInterfaceTypeArguments"() {
        when:
        def argument = ReflectionArguments.resolveGenericToArgument(B, Iface)

        then:
        argument.type == Iface
        argument.typeParameters.length == 1
        argument.typeParameters[0].type == String
        argument.typeParameters*.type as Class[] == GenericTypeUtils.resolveInterfaceTypeArguments(B, Iface)
    }

    static interface Iface<T> {}

    static abstract class A<T> implements Iface<T> {}

    static class B extends A<String> implements Iface<String> {}

    // =======================

    void "test resolveSuperGenericTypeArgument"() {
        given:
        def argument = ReflectionArguments.resolveGenericToArgument(Baz, Bar)

        expect:
        argument.typeParameters[0].type == GenericTypeUtils.resolveSuperGenericTypeArgument(Baz).get()
    }

    void "test resolveInterfaceTypeArgument"() {
        given:
        def argument = ReflectionArguments.resolveGenericToArgument(B, Iface)

        expect:
        argument.typeParameters[0].type == GenericTypeUtils.resolveInterfaceTypeArgument(B, Iface).get()
    }

    void "test resolveGenericTypeArgument of a field"() {
        given:
        def field = Holder.getDeclaredField("supplier")

        expect:
        ReflectionArguments.of(field).typeParameters[0].type == GenericTypeUtils.resolveGenericTypeArgument(field).get()
    }

    void "test resolveTypeArguments of a type"() {
        given:
        def type = Holder.getDeclaredField("supplier").genericType

        expect:
        ReflectionArguments.of(type).typeParameters*.type as Class[] == GenericTypeUtils.resolveTypeArguments(type)
    }

    static class Holder {
        private Supplier<String> supplier
    }

    // =======================

    void "an argument bound at an intermediate generic type is found, where GenericTypeUtils finds nothing"() {
        expect: "Leaf extends Mid<String>, and Mid - not Leaf - is what implements Iface<T>"
        GenericTypeUtils.resolveInterfaceTypeArguments(Leaf, Iface) == [] as Class[]
        GenericTypeUtils.resolveInterfaceTypeArgument(Leaf, Iface).isEmpty()

        and: "the variable is substituted, so the argument is the one Leaf gives it"
        ReflectionArguments.resolveGenericToArgument(Leaf, Iface).typeParameters*.type == [String]
    }

    void "a super type argument bound at an intermediate generic type is found too"() {
        expect: "Baz extends Bar<String>, and Bar - not Baz - is what extends Foo<T>"
        GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Foo) == [] as Class[]

        and:
        ReflectionArguments.resolveGenericToArgument(Baz, Foo).typeParameters*.type == [String]
    }

    static abstract class Mid<T> implements Iface<T> {}

    static class Leaf extends Mid<String> {}

    // =======================

    void "the argument keeps what the erasure loses"() {
        when: "a super type argument is itself parameterized"
        def argument = ReflectionArguments.resolveGenericToArgument(Nested, Supplier)

        then: "GenericTypeUtils answers the raw class"
        GenericTypeUtils.resolveInterfaceTypeArguments(Nested, Supplier) == [List] as Class[]

        and: "the argument answers the whole type"
        argument.typeParameters[0].type == List
        argument.typeParameters[0].typeParameters*.type == [String]
    }

    static class Nested implements Supplier<List<String>> {
        @Override
        List<String> get() {
            return null
        }
    }
}
