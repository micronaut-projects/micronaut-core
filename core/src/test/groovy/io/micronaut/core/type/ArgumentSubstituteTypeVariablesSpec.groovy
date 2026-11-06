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
import spock.lang.Shared
import spock.lang.Specification

/**
 * {@link Argument#substituteTypeVariables(Map)} replaces the type variables of one declaration at any depth, and
 * keeps everything else of the way the argument was written.
 */
class ArgumentSubstituteTypeVariablesSpec extends Specification {

    @Shared
    AnnotationMetadata onUse = metadata('test.OnUse')

    @Shared
    AnnotationMetadata onType = metadata('test.OnType')

    private static AnnotationMetadata metadata(String annotation) {
        new AnnotationMetadata() {
            @Override boolean hasAnnotation(String name) { name == annotation }
            @Override boolean isEmpty() { false }
        }
    }

    private static Argument<?> variable(String name, Argument<?>... bounds) {
        Class<?> erasure = bounds.length == 0 ? Object : bounds[0].type
        Argument.ofTypeVariable(erasure, name, name, null, null, bounds.length == 0 ? null : bounds)
    }

    private static Argument<?> upper(Argument<?> bound) {
        Argument.ofWildcard(bound.type, null, null, bound.typeParameters, [bound] as Argument[], null)
    }

    private static Argument<?> lower(Argument<?> bound) {
        Argument.ofWildcard(Object, null, null, null, null, [bound] as Argument[])
    }

    private static Argument<?> type(Class<?> type, Argument<?>... typeArguments) {
        Argument.of(type, (String) null, typeArguments)
    }

    void "a variable is replaced in the type arguments, the bounds of a wildcard and the component of an array"() {
        given: 'Map<K, ? extends List<V>[]>'
        Argument<?> declared = type(Map, variable('K'), upper(type(List, variable('V')).arrayType()))

        when:
        Argument<?> substituted = declared.substituteTypeVariables(K: Argument.STRING, V: Argument.of(Integer))

        then: 'Map<String, ? extends List<Integer>[]>'
        substituted.equalsStructure(type(Map, Argument.STRING, upper(type(List, Argument.of(Integer)).arrayType())))
        substituted.typeParameters[1].isWildcard()
    }

    void "a lower bound is substituted too"() {
        expect: 'List<? super T> with T := String is List<? super String>'
        type(List, lower(variable('T'))).substituteTypeVariables(T: Argument.STRING)
            .equalsStructure(type(List, lower(Argument.STRING)))
    }

    void "an array of a variable is the array of the type given"() {
        given:
        Argument<?> listOfString = Argument.listOf(String)

        when:
        Argument<?> array = variable('T').arrayType().substituteTypeVariables(T: listOfString)
        Argument<?> arrays = variable('T').arrayType().arrayType().substituteTypeVariables(T: listOfString)

        then: 'List<String>[] and List<String>[][]'
        array.type == List[]
        array.componentType().equalsStructure(listOfString)
        arrays.type == List[][]
        arrays.componentType().componentType().equalsStructure(listOfString)
    }

    void "an array of a variable given a wildcard is a wildcard bounded by the arrays of its bounds"() {
        when:
        Argument<?> substituted = variable('T').arrayType().substituteTypeVariables(T: upper(Argument.of(Number)))

        then:
        substituted instanceof WildcardArgument
        ((WildcardArgument<?>) substituted).upperBounds*.type == [Number[]]
    }

    void "the substitution is simultaneous and not applied to the types it gives"() {
        given:
        Argument<?> listOfU = type(List, variable('U'))
        Argument<?> listOfT = type(List, variable('T'))

        expect: 'Map<T, U> with T := List<U>, U := String is Map<List<U>, String>'
        type(Map, variable('T'), variable('U')).substituteTypeVariables(T: listOfU, U: Argument.STRING)
            .equalsStructure(type(Map, listOfU, Argument.STRING))

        and: 'T := List<T> gives List<T> once'
        variable('T').substituteTypeVariables(T: listOfT).equalsStructure(listOfT)
    }

    void "a variable that is not named is kept as it is, its bounds included"() {
        given: 'U extends Comparable<T>, whose bound names a variable that is substituted'
        Argument<?> u = variable('U', type(Comparable, variable('T')))

        expect:
        u.substituteTypeVariables(T: Argument.STRING).is(u)
        type(List, u).substituteTypeVariables(T: Argument.STRING).typeParameters[0].is(u)
    }

    void "a variable with a recursive bound is replaced whole"() {
        given: 'T extends Comparable<T>'
        Argument<?> t = variable('T', type(Comparable, variable('T')))

        expect:
        type(List, t).substituteTypeVariables(T: Argument.of(Integer)).equalsStructure(type(List, Argument.of(Integer)))
    }

    void "a wildcard named after a type parameter is not a variable of that name"() {
        given: 'the List<?> of a parameter, its wildcard named after the E of List<E>'
        Argument<?> wildcard = Argument.ofWildcard(Object, 'E', null, null, null, null)
        Argument<?> declared = Argument.of(List, 'list', wildcard)

        when:
        Argument<?> substituted = declared.substituteTypeVariables(E: Argument.STRING)

        then:
        substituted.typeParameters[0].isWildcard()
        substituted.equalsStructure(declared)
    }

    void "a type resolved in place of a variable is not a variable, its type arguments are substituted"() {
        given: 'the List<U> resolved in place of a T'
        Argument<?> resolved = Argument.ofResolvedTypeVariable(List, 'T', 'T', null, [variable('U')] as Argument[], null)

        when:
        Argument<?> substituted = resolved.substituteTypeVariables(T: Argument.STRING, U: Argument.of(Integer))

        then:
        substituted instanceof GenericPlaceholder
        ((GenericPlaceholder<?>) substituted).resolved
        substituted.type == List
        substituted.typeParameters[0].equalsStructure(Argument.of(Integer))
    }

    void "a raw type keeps the variables its class declares"() {
        given:
        Argument<?> raw = Argument.ofRawType(List, 'list', null, [variable('E')] as Argument[])

        expect:
        raw.substituteTypeVariables(E: Argument.STRING).is(raw)
    }

    void "the type given takes the name of the use and the annotations written on it"() {
        given: 'a parameter @OnUse T value, and the E of List<T>'
        Argument<?> use = Argument.ofTypeVariable(Object, 'value', 'T', onUse, null, null)
        Argument<?> list = Argument.of(List, 'items', Argument.ofTypeVariable(Object, 'E', 'T'))
        Argument<?> annotatedString = Argument.of(String, 'other', onType)

        when:
        Argument<?> substituted = use.substituteTypeVariables(T: annotatedString)
        Argument<?> typeArgument = list.substituteTypeVariables(T: Argument.STRING).typeParameters[0]

        then: 'the annotations of the use win over those of the type given'
        substituted.type == String
        substituted.name == 'value'
        substituted.annotationMetadata.is(onUse)
        !substituted.isTypeVariable()

        and: 'a type argument is named after the type parameter it stands in'
        typeArgument.type == String
        typeArgument.name == 'E'
        list.substituteTypeVariables(T: Argument.STRING).typeVariables.get('E').type == String
    }

    void "a use without annotations keeps those of the type given"() {
        given:
        Argument<?> use = Argument.ofTypeVariable(Object, 'value', 'T')

        when:
        Argument<?> substituted = use.substituteTypeVariables(T: Argument.of(String, 'other', onType))

        then:
        substituted.annotationMetadata.is(onType)
        substituted.name == 'value'
    }

    void "the shape, the name and the annotations of the argument itself are kept"() {
        given:
        Argument<?> declared = Argument.of(Map, 'map', onUse, variable('K'), variable('V'))

        when:
        Argument<?> substituted = declared.substituteTypeVariables(K: Argument.STRING, V: Argument.of(Integer))

        then:
        substituted.name == 'map'
        substituted.annotationMetadata.is(onUse)
        substituted.equalsStructure(Argument.mapOf(String, Integer))
    }

    void "nothing to substitute answers the argument itself"() {
        given:
        Argument<?> declared = type(List, variable('T'))

        expect:
        declared.substituteTypeVariables([:]).is(declared)
    }

    void "the substitutions are required"() {
        when:
        Argument.STRING.substituteTypeVariables(null)

        then:
        thrown(NullPointerException)
    }
}
