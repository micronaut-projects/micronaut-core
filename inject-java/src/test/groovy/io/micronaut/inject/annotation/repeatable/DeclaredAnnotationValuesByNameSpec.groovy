/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

/**
 * {@code getDeclaredAnnotationValuesByName} answers exactly like {@code getAnnotationValuesByName} for an
 * annotation the element declares itself: a non-repeatable annotation is a single-element list, a repeatable
 * one lists its occurrences whether written once, twice or through its container, and a missing name is empty.
 */
class DeclaredAnnotationValuesByNameSpec extends AbstractTypeElementSpec {

    private static final String PKG = 'byname'
    private static final String FOO = PKG + '.Foo'
    private static final String Q = PKG + '.Q'
    private static final String QS = PKG + '.Qs'
    private static final String MISSING = PKG + '.Missing'

    private static final String ANNOTATIONS = '''
@Retention(RetentionPolicy.RUNTIME)
@interface Foo {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Qs.class)
@interface Q {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Qs {
    Q[] value();
}
'''

    private static String source(String annotations) {
        """
package $PKG;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.inject.Singleton;

@Singleton
$annotations
class Bar {
}
$ANNOTATIONS
"""
    }

    void "declared and inherited queries by name agree at compile time for #description"() {
        given:
        AnnotationMetadata metadata = buildClassElement(source(annotations)).getAnnotationMetadata()

        expect:
        probe(metadata, name) == [declared: expected, all: expected]

        where:
        description                        | annotations                     | name    || expected
        'a non-repeatable annotation'      | '@Foo("x")'                     | FOO     || ['x']
        'a repeatable written once'        | '@Q("a")'                       | Q       || ['a']
        'a repeatable written twice'       | '@Q("a") @Q("b")'               | Q       || ['a', 'b']
        'a container written by hand'      | '@Qs({@Q("a"), @Q("b")})'       | Q       || ['a', 'b']
        'a missing annotation'             | '@Foo("x")'                     | MISSING || []
        'a repeatable that is not present' | '@Foo("x")'                     | Q       || []
    }

    void "declared and inherited queries by name agree in the generated definition for #description"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition("${PKG}.Bar", source(annotations))
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        expect:
        probe(metadata, name) == [declared: expected, all: expected]

        where:
        description                        | annotations                     | name    || expected
        'a non-repeatable annotation'      | '@Foo("x")'                     | FOO     || ['x']
        'a repeatable written once'        | '@Q("a")'                       | Q       || ['a']
        'a repeatable written twice'       | '@Q("a") @Q("b")'               | Q       || ['a', 'b']
        'a container written by hand'      | '@Qs({@Q("a"), @Q("b")})'       | Q       || ['a', 'b']
        'a missing annotation'             | '@Foo("x")'                     | MISSING || []
        'a repeatable that is not present' | '@Foo("x")'                     | Q       || []
    }

    void "the declared annotation values by name carry the annotation name"() {
        given:
        AnnotationMetadata metadata = buildClassElement(source('@Foo("x")')).getAnnotationMetadata()

        expect:
        [
                declared: metadata.getDeclaredAnnotationValuesByName(FOO)*.getAnnotationName(),
                all     : metadata.getAnnotationValuesByName(FOO)*.getAnnotationName(),
        ] == [declared: [FOO], all: [FOO]]
    }

    private static Map<String, List<String>> probe(AnnotationMetadata metadata, String name) {
        [
                declared: metadata.getDeclaredAnnotationValuesByName(name).collect { it.stringValue().get() },
                all     : metadata.getAnnotationValuesByName(name).collect { it.stringValue().get() },
        ]
    }
}
