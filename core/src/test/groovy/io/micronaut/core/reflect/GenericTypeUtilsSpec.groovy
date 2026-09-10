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
package io.micronaut.core.reflect

import spock.lang.Specification

class GenericTypeUtilsSpec extends Specification {

    void "test resolve generic super type"() {
        expect:
        GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Bar) == [String] as Class[]
    }

    static class Foo<T> {}

    static class Bar<T> extends Foo<T> {}

    static class Baz extends Bar<String> {}

    // =======================

    // https://github.com/micronaut-projects/micronaut-openapi/issues/238
    void "test resolveInterfaceTypeArguments"() {
        when:
        Class[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(B, Iface)

        then:
        classes.length == 1
        classes[0] == [String] as Class[]
    }

    static interface Iface<T> {}

    static abstract class A<T> implements Iface<T> {}

    static class B extends A<String> implements Iface<String> {}

    // =======================

    void "test resolveTypeArguments substitutes the bindings of every level"() {
        expect: 'an argument bound at an intermediate generic type, which the deprecated methods lose'
        GenericTypeUtils.resolveTypeArguments(Leaf, Iface) == [String] as Class[]
        GenericTypeUtils.resolveTypeArguments(Baz, Foo) == [String] as Class[]
        and: 'the deprecated methods answering nothing is what this replaces'
        GenericTypeUtils.resolveInterfaceTypeArguments(Leaf, Iface) == [] as Class[]
        GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Foo) == [] as Class[]
    }

    void "test resolveTypeArguments reads a directly bound type as the deprecated methods do"() {
        expect:
        GenericTypeUtils.resolveTypeArguments(B, Iface) == [String] as Class[]
        GenericTypeUtils.resolveTypeArguments(Baz, Bar) == [String] as Class[]
        GenericTypeUtils.resolveTypeArguments(Both, First) == [String] as Class[]
        GenericTypeUtils.resolveTypeArguments(Both, Second) == [Integer] as Class[]
    }

    void "test resolveTypeArguments answers nothing when there is nothing to answer"() {
        expect: 'not a super type, no type parameter, raw, and an argument left unresolved'
        GenericTypeUtils.resolveTypeArguments(Baz, Iface) == [] as Class[]
        GenericTypeUtils.resolveTypeArguments(Leaf, Runnable) == [] as Class[]
        GenericTypeUtils.resolveTypeArguments(Raw, Iface) == [] as Class[]
        GenericTypeUtils.resolveTypeArguments(Mid, Iface) == [] as Class[]
        GenericTypeUtils.resolveTypeArguments(null, Iface) == [] as Class[]
        GenericTypeUtils.resolveTypeArguments(Leaf, null) == [] as Class[]
    }

    static class Mid<T> implements Iface<T> {}

    static class Leaf extends Mid<String> {}

    static class Raw implements Iface {}

    static interface First<T> {}

    static interface Second<T> {}

    static abstract class FirstAndSecond<X, Y> implements First<X>, Second<Y> {}

    static class Both extends FirstAndSecond<String, Integer> {}
}

