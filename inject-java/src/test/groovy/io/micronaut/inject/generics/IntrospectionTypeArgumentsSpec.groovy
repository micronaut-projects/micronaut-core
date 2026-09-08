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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import java.util.function.Function

class IntrospectionTypeArgumentsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.util.Iterator;

interface Validator<A, T> {
    boolean isValid(T value);
}

interface StringIterable extends Iterable<String> {
}

@Introspected
@Singleton
class Bound implements Validator<CharSequence, Number> {
    public boolean isValid(Number value) { return true; }
}

@Introspected
@Singleton
class Indirect implements StringIterable {
    public Iterator<String> iterator() { return null; }
}

@Introspected
@Singleton
class Open<T> implements Validator<CharSequence, T> {
    public boolean isValid(T value) { return true; }
}

@Introspected
@Singleton
class Plain {
}
'''

    void "an introspected type reports the arguments it binds in an interface"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments('test.Validator')*.type == [CharSequence, Number]
        introspection.getTypeArguments('test.Validator')*.name == ['A', 'T']
    }

    void "an introspection reports the same arguments as the bean definition for the same class"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)
        def definition = buildBeanDefinition('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments('test.Validator') == definition.getTypeArguments('test.Validator')
        introspection.getTypeArguments() == definition.getTypeArguments()
    }

    void "arguments bound through an intermediate interface are reported"() {
        given:
        def introspection = buildBeanIntrospection('test.Indirect', SOURCE)

        expect:
        introspection.getTypeArguments(Iterable)*.type == [String]
        introspection.getTypeArguments(Iterable) == buildBeanDefinition('test.Indirect', SOURCE).getTypeArguments(Iterable)
    }

    void "an argument left open is reported as the type variable"() {
        given:
        def introspection = buildBeanIntrospection('test.Open', SOURCE)
        def definition = buildBeanDefinition('test.Open', SOURCE)
        def arguments = introspection.getTypeArguments('test.Validator')

        expect:
        arguments*.type == [CharSequence, Object]
        arguments[1].isTypeVariable()
        arguments == definition.getTypeArguments('test.Validator')

        and: "the no-argument overload reports what the type itself declares"
        introspection.getTypeArguments()*.name == ['T']
        introspection.getTypeArguments() == definition.getTypeArguments()
    }

    void "an introspected type that binds nothing reports an empty list"() {
        given:
        def introspection = buildBeanIntrospection('test.Plain', SOURCE)

        expect:
        introspection.getTypeArguments() == []
        introspection.getTypeArguments('test.Validator') == []
        introspection.getTypeArguments(Iterable) == []
        introspection.getTypeArguments((Class) null) == []
        introspection.getTypeArguments((String) null) == []
    }

    void "an introspection generated for a type that is not itself annotated reports its bindings"() {
        given:
        def introspection = buildBeanIntrospection('test.$test_Outer$Inner', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.function.Function;

@Introspected(classes = Outer.Inner.class)
class Outer {
    static class Inner implements Function<String, Integer> {
        public Integer apply(String s) { return 1; }
    }
}
''')

        expect:
        introspection.getTypeArguments(Function)*.type == [String, Integer]
    }

    void "a type that binds nothing anywhere still answers for a type it does not implement"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments(Comparable) == []
        introspection.getTypeArguments('does.not.Exist') == []
    }
}
