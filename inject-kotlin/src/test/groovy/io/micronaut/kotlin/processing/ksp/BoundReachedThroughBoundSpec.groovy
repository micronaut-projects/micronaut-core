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
package io.micronaut.kotlin.processing.ksp

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument

/**
 * A variable whose bound names it, met inside the bound of another variable, keeps naming itself in its bound.
 */
class BoundReachedThroughBoundSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.Introspected

@Introspected
class Fuzz<T, V : List<T>>(val t: T, val v: V) where T : Number, T : Comparable<T>
'''

    private static GenericPlaceholder<?> placeholder(Argument<?> argument) {
        assert argument instanceof GenericPlaceholder
        return (GenericPlaceholder<?>) argument
    }

    private static void assertSelfBounded(GenericPlaceholder<?> t) {
        assert t.variableName == 'T'
        assert t.bounds*.type == [Number, Comparable]
        GenericPlaceholder<?> inner = placeholder(t.bounds[1].typeParameters[0])
        assert inner.variableName == 'T'
        assert !inner.resolved
    }

    void "the variable met directly names itself in its bound"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Fuzz', SOURCE)

        expect:
        assertSelfBounded(placeholder(introspection.getRequiredProperty('t', Object).asArgument()))
    }

    void "the variable met through the bound of another variable names itself in its bound"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Fuzz', SOURCE)
        GenericPlaceholder<?> v = placeholder(introspection.getRequiredProperty('v', Object).asArgument())

        expect:
        v.variableName == 'V'
        v.bounds*.type == [List]

        when:
        Argument<?> wildcard = v.bounds[0].typeParameters[0]
        Argument<?> t = wildcard instanceof WildcardArgument ? ((WildcardArgument<?>) wildcard).upperBounds[0] : wildcard

        then:
        assertSelfBounded(placeholder(t))
    }
}
