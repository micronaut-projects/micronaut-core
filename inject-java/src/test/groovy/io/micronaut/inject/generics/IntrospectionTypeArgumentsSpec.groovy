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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.ast.ClassElement

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

    private static final String HIERARCHY = '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

interface Container<E, F> {
}

interface Flipped<F, E> extends Container<E, F> {
}

@Introspected
@Singleton
class Reversed<A, B> extends HashMap<B, A> {
}

@Introspected
@Singleton
class Swapped<K, V> extends HashMap<V, K> {
}

@Introspected
@Singleton
class Holder<X, Y> implements Flipped<X, Y> {
}

@Introspected
@Singleton
class Strings extends ArrayList<String> {
}

@Introspected
@Singleton
class Nested<X> extends ArrayList<List<X>> {
}
'''

    private static final String ANNOTATED = '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE})
@interface Marker {
}

@Introspected
@Singleton
class AnnotatedLeaf<X> implements AnnotatedMiddle<X> {
}

interface Container2<E, F> {
}

interface AnnotatedMiddle<T> extends Container2<@Marker T, T> {
}

@Introspected
@Singleton
class AnnotatedStrings implements AnnotatedMiddle<String> {
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

    void "a type two levels above is reported in the variables of the introspected type"() {
        given:
        def introspection = buildBeanIntrospection('test.Reversed', HIERARCHY)
        def definition = buildBeanDefinition('test.Reversed', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(HashMap)) == ['B', 'A']
        introspection.getTypeArguments(Map)*.name == ['K', 'V']
        variableNames(introspection.getTypeArguments(Map)) == ['B', 'A']
        variableNames(definition.getTypeArguments(Map)) == ['B', 'A']
        introspection.getTypeArguments(Map) == definition.getTypeArguments(Map)
    }

    void "a variable of the introspected type named like one of the super type is still bound by position"() {
        given:
        def introspection = buildBeanIntrospection('test.Swapped', HIERARCHY)
        def definition = buildBeanDefinition('test.Swapped', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(HashMap)) == ['V', 'K']
        variableNames(introspection.getTypeArguments(Map)) == ['V', 'K']
        variableNames(definition.getTypeArguments(Map)) == ['V', 'K']
    }

    void "an interface reached through an intermediate interface is reported in the variables of the introspected type"() {
        given:
        def introspection = buildBeanIntrospection('test.Holder', HIERARCHY)
        def definition = buildBeanDefinition('test.Holder', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments('test.Flipped')) == ['X', 'Y']
        introspection.getTypeArguments('test.Container')*.name == ['E', 'F']
        variableNames(introspection.getTypeArguments('test.Container')) == ['Y', 'X']
        variableNames(definition.getTypeArguments('test.Container')) == ['Y', 'X']
    }

    void "a concrete type bound one level up is reported for every type above"() {
        given:
        def introspection = buildBeanIntrospection('test.Strings', HIERARCHY)
        def definition = buildBeanDefinition('test.Strings', HIERARCHY)

        expect:
        introspection.getTypeArguments(Iterable)*.type == [String]
        !introspection.getTypeArguments(Iterable)[0].isTypeVariable()
        introspection.getTypeArguments(Collection)*.type == [String]
        definition.getTypeArguments(Iterable)*.type == [String]
    }

    void "a variable nested in a type bound one level up is reported in the variables of the introspected type"() {
        given:
        def introspection = buildBeanIntrospection('test.Nested', HIERARCHY)
        def definition = buildBeanDefinition('test.Nested', HIERARCHY)
        def element = introspection.getTypeArguments(Iterable)[0]

        expect:
        element.type == List
        variableNames(element.typeParameters as List) == ['X']
        variableNames(definition.getTypeArguments(Iterable)[0].typeParameters as List) == ['X']
    }

    void "a type that binds nothing anywhere still answers for a type it does not implement"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments(Comparable) == []
        introspection.getTypeArguments('does.not.Exist') == []
    }

    void "type annotations written where a variable is used survive the binding"() {
        given:
        def introspection = buildBeanIntrospection('test.AnnotatedLeaf', ANNOTATED)
        def definition = buildBeanDefinition('test.AnnotatedLeaf', ANNOTATED)
        def arguments = introspection.getTypeArguments('test.Container2')

        expect: "the annotation written where the intermediate interface uses the variable is kept"
        arguments[0].annotationMetadata.hasAnnotation('test.Marker')
        !arguments[1].annotationMetadata.hasAnnotation('test.Marker')
        definition.getTypeArguments('test.Container2')[0].annotationMetadata.hasAnnotation('test.Marker')

        and: "the argument the use does not annotate is the variable of the introspected type"
        variableNames(arguments)[1] == 'X'
    }

    void "a use that annotates the variable is answered as the intermediate type writes it"() {
        expect:
        buildClassElement('''
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class Test<X> implements AnnotatedMiddle<X> {
}

interface Container2<E, F> {
}

interface AnnotatedMiddle<T> extends Container2<@Marker T, T> {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE})
@interface Marker {
}
''') { ClassElement leaf ->
            def arguments = leaf.getAllTypeArguments().get('test.Container2')

            assert arguments.E.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker')
            assert !arguments.F.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker')

            and: "the annotations of a use are carried by the use alone, so it keeps the name it is written with"
            assert arguments.values()*.variableName == ['T', 'X']
            return true
        }
    }

    void "a variable bound to a concrete type is reported for every type above"() {
        given:
        def introspection = buildBeanIntrospection('test.AnnotatedStrings', ANNOTATED)
        def definition = buildBeanDefinition('test.AnnotatedStrings', ANNOTATED)

        expect:
        introspection.getTypeArguments('test.Container2')*.type == [String, String]
        definition.getTypeArguments('test.Container2')*.type == [String, String]
    }

    private static List<String> variableNames(List<Argument<?>> arguments) {
        arguments.collect { it instanceof GenericPlaceholder ? it.variableName : null }
    }
}
