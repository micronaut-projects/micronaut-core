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

import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.Specification

/**
 * An array carrying the component it was written with, which {@link Argument#componentType()} answers in place of
 * the one it rebuilds, with the annotations of that component.
 */
class ArgumentComponentTypeSpec extends Specification {

    void "an array answers the component it was given"() {
        given:
        AnnotationMetadata annotated = Stub(AnnotationMetadata)
        Argument<?> component = Argument.of(String, 'element', annotated)
        Argument<?> array = Argument.of(String[], 'names').withComponentType(component)

        expect:
        array.componentType().is(component)
        array.componentType().annotationMetadata.is(annotated)
        array.name == 'names'
        array.type == String[]
        array == Argument.of(String[], 'names')
        array.equalsStructure(Argument.of(String[]))
    }

    void "an array without a component given rebuilds one without annotations"() {
        given:
        Argument<?> array = Argument.of(String[], 'names', Stub(AnnotationMetadata))

        expect:
        array.componentType().type == String
        array.componentType().annotationMetadata.is(AnnotationMetadata.EMPTY_METADATA)
        !array.componentType().is(array.componentType())
    }

    void "the component is kept by a renamed or re-annotated array of each kind"() {
        given:
        Argument<?> component = Argument.of(componentType, 'element', Stub(AnnotationMetadata))
        Argument<?> withComponent = array.withComponentType(component)

        expect:
        withComponent.class == array.class
        withComponent.componentType().is(component)
        withComponent.withName('other').componentType().is(component)
        withComponent.withName('other').class == array.class
        withComponent.withAnnotationMetadata(Stub(AnnotationMetadata)).componentType().is(component)
        withComponent.withAnnotationMetadata(Stub(AnnotationMetadata)).class == array.class
        withComponent.isRawType() == array.isRawType()
        withComponent.isTypeVariable() == array.isTypeVariable()

        where:
        array                                                                         | componentType
        Argument.of(String[], 'names')                                                | String
        Argument.of(List[], 'lists', null, Argument.of(String))                       | List
        Argument.ofRawType(List[], 'lists', null, null)                               | List
        Argument.ofTypeVariable(CharSequence[], 'values', 'T', null, null,
            [Argument.of(CharSequence)] as Argument[])                                | CharSequence
        Argument.ofResolvedTypeVariable(String[], 'values', 'T', null, null, null)    | String
        Argument.of(int[][], 'matrix')                                                | int[]
    }

    void "a raw array keeps the name it was given when re-annotated"() {
        given:
        Argument<?> array = Argument.ofRawType(List[], null, null, null)
            .withComponentType(Argument.ofRawType(List, null, null, null))

        expect:
        array.withAnnotationMetadata(Stub(AnnotationMetadata)) == Argument.ofRawType(List[], null, null, null)
    }

    void "the component of an array of arrays carries its own component"() {
        given:
        Argument<?> leaf = Argument.of(String, 'leaf', Stub(AnnotationMetadata))
        Argument<?> middle = Argument.of(String[], 'middle', Stub(AnnotationMetadata)).withComponentType(leaf)
        Argument<?> outer = Argument.of(String[][], 'outer').withComponentType(middle)

        expect:
        outer.componentType().is(middle)
        outer.componentType().componentType().is(leaf)
    }

    void "a component of another type than the array is rejected"() {
        when:
        array.withComponentType(component)

        then:
        thrown(IllegalArgumentException)

        where:
        array                                                       | component
        Argument.of(String[])                                       | Argument.of(Integer)
        Argument.of(String)                                         | Argument.of(Character)
        Argument.of(String[][])                                     | Argument.of(String)
        Argument.ofWildcard(Object[], null, null, null, null, null) | Argument.of(Object)
    }

    void "an argument that cannot carry a component answers itself"() {
        given:
        Argument<String[]> array = new Argument<String[]>() {
            @Override
            String getName() { 'names' }

            @Override
            Class<String[]> getType() { String[] }

            @Override
            Argument<?>[] getTypeParameters() { Argument.ZERO_ARGUMENTS }

            @Override
            Map<String, Argument<?>> getTypeVariables() { [:] }

            @Override
            boolean equalsType(Argument<?> other) { other.type == String[] }

            @Override
            int typeHashCode() { String[].hashCode() }
        }

        expect:
        array.withComponentType(Argument.of(String, 'element', Stub(AnnotationMetadata))).is(array)
        array.componentType().type == String

        when:
        array.withComponentType(Argument.of(Integer))

        then:
        thrown(IllegalArgumentException)
    }
}
