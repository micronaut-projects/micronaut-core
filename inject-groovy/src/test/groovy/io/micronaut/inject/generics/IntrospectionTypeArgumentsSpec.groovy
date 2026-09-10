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
package io.micronaut.inject.generics

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class IntrospectionTypeArgumentsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

interface Validator<A, T> {
    boolean isValid(T value)
}

interface StringIterable extends Iterable<String> {
}

@Introspected
@Singleton
class Bound implements Validator<CharSequence, Number> {
    boolean isValid(Number value) { true }
}

@Introspected
@Singleton
class Indirect implements StringIterable {
    Iterator<String> iterator() { null }
}

@Introspected
@Singleton
class Plain {
}
'''

    void "an introspected type reports the arguments it binds in an interface"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)
        def definition = buildBeanDefinition('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments('test.Validator')*.type == [CharSequence, Number]
        introspection.getTypeArguments('test.Validator') == definition.getTypeArguments('test.Validator')
    }

    void "arguments bound through an intermediate interface are reported"() {
        given:
        def introspection = buildBeanIntrospection('test.Indirect', SOURCE)

        expect:
        introspection.getTypeArguments(Iterable)*.type == [String]
    }

    void "an introspected type that binds nothing reports an empty list"() {
        given:
        def introspection = buildBeanIntrospection('test.Plain', SOURCE)

        expect:
        introspection.getTypeArguments() == []
        introspection.getTypeArguments('test.Validator') == []
    }
}
