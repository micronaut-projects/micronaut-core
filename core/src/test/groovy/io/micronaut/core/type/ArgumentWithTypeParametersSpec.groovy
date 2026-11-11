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
import spock.lang.Unroll

/**
 * {@link Argument#withTypeParameters(Argument[])} replaces the type parameters and keeps everything else of the
 * way the argument was written.
 */
class ArgumentWithTypeParametersSpec extends Specification {

    @Shared
    AnnotationMetadata annotated = new AnnotationMetadata() {
        @Override boolean hasAnnotation(String annotation) { true }
    }

    void "a wildcard stays a wildcard with its upper and lower bounds"() {
        given:
        Argument<?>[] upper = [Argument.of(Comparable, 'T', Argument.of(String, 'T'))]
        Argument<?>[] lower = [Argument.of(CharSequence)]
        Argument<?> wildcard = Argument.ofWildcard(Comparable, 'E', annotated, [Argument.of(String, 'T')] as Argument[], upper, lower)

        when:
        Argument<?> copy = wildcard.withTypeParameters(Argument.of(Integer, 'T'))

        then:
        copy instanceof WildcardArgument
        copy instanceof GenericPlaceholder
        copy.typeVariable
        copy.type == Comparable
        copy.name == 'E'
        copy.annotationMetadata.is(annotated)
        copy.typeParameters == [Argument.of(Integer, 'T')] as Argument[]
        ((WildcardArgument<?>) copy).upperBounds == upper.toList()
        ((WildcardArgument<?>) copy).lowerBounds == lower.toList()
    }

    void "an unbounded wildcard keeps Object as its upper bound and no lower bound"() {
        given:
        Argument<?> wildcard = Argument.ofWildcard(Object, 'E', null, null, null, null)

        when:
        WildcardArgument<?> copy = (WildcardArgument<?>) wildcard.withTypeParameters()

        then:
        copy.upperBounds == [Argument.OBJECT_ARGUMENT]
        copy.lowerBounds.isEmpty()
        copy == wildcard
    }

    @Unroll
    void "a type variable keeps its variable name, its bounds and whether it is resolved (resolved: #resolved)"() {
        given:
        Argument<?>[] bounds = [Argument.of(Comparable, 'T', Argument.of(Number, 'T')), Argument.of(Serializable)]
        Argument<?> variable = resolved
                ? Argument.ofResolvedTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(Number, 'T')] as Argument[], bounds)
                : Argument.ofTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(Number, 'T')] as Argument[], bounds)

        when:
        Argument<?> copy = variable.withTypeParameters(Argument.of(Long, 'T'))

        then:
        copy instanceof GenericPlaceholder
        !(copy instanceof WildcardArgument)
        copy.typeVariable
        copy.type == Comparable
        copy.name == 'value'
        copy.annotationMetadata.is(annotated)
        copy.typeParameters == [Argument.of(Long, 'T')] as Argument[]
        ((GenericPlaceholder<?>) copy).variableName == 'X'
        ((GenericPlaceholder<?>) copy).bounds == bounds.toList()
        ((GenericPlaceholder<?>) copy).resolved == resolved

        where:
        resolved << [false, true]
    }

    void "a type variable of no recorded bounds still answers its erasure"() {
        given:
        Argument<?> variable = Argument.ofTypeVariable(Number, 'T')

        when:
        GenericPlaceholder<?> copy = (GenericPlaceholder<?>) variable.withTypeParameters()

        then:
        copy.variableName == 'T'
        copy.bounds == ((GenericPlaceholder<?>) variable).bounds
        !copy.resolved
    }

    void "a raw type stays raw"() {
        given:
        Argument<?> raw = Argument.ofRawType(List, 'list', annotated, [Argument.of(Object, 'E')] as Argument[])

        when:
        Argument<?> copy = raw.withTypeParameters(Argument.of(String, 'E'))

        then:
        copy.rawType
        !copy.typeVariable
        copy.name == 'list'
        copy.annotationMetadata.is(annotated)
        copy.typeParameters == [Argument.of(String, 'E')] as Argument[]
    }

    void "an argument that answers raw without being built raw stays raw"() {
        given:
        Argument<?> raw = new DefaultArgument<List>(List, 'list', annotated, Argument.of(Object, 'E')) {
            @Override boolean isRawType() { true }
        }

        when:
        Argument<?> copy = raw.withTypeParameters(Argument.of(String, 'E'))

        then:
        copy.rawType
        copy.name == 'list'
        copy.annotationMetadata.is(annotated)
        copy.typeParameters == [Argument.of(String, 'E')] as Argument[]
    }

    void "a plain argument stays plain and keeps being a type variable when it was one"() {
        given:
        Argument<?> plain = Argument.of(Map, 'map', annotated, Argument.of(String, 'K'), Argument.of(Integer, 'V'))
        Argument<?> typeVariable = new DefaultArgument<>(List, 'E', annotated, true, Argument.of(String, 'E'))

        when:
        Argument<?> copy = plain.withTypeParameters(Argument.of(Long, 'K'), Argument.of(Double, 'V'))

        then:
        !copy.rawType
        !copy.typeVariable
        !(copy instanceof GenericPlaceholder)
        copy.name == 'map'
        copy.annotationMetadata.is(annotated)
        copy.typeParameters == [Argument.of(Long, 'K'), Argument.of(Double, 'V')] as Argument[]
        copy.typeVariables == [K: Argument.of(Long, 'K'), V: Argument.of(Double, 'V')]

        and:
        typeVariable.withTypeParameters(Argument.of(Integer, 'E')).typeVariable
    }

    void "an array keeps the relation to its component"() {
        given:
        Argument<?> array = Argument.of(List[], 'lists', annotated, Argument.of(String, 'E'))

        when:
        Argument<?> copy = array.withTypeParameters(Argument.of(Integer, 'E'))

        then:
        copy.type == List[]
        copy.array
        copy.name == 'lists'
        copy.annotationMetadata.is(annotated)
        copy.componentType().equalsStructure(Argument.of(List, (String) null, Argument.of(Integer)))
        copy.equalsStructure(Argument.of(List, (String) null, Argument.of(Integer)).arrayType())
    }

    void "an array of a type variable stays an array of that variable"() {
        given:
        Argument<?>[] bounds = [Argument.of(Comparable, 'T', Argument.of(String, 'T'))]
        Argument<?> array = Argument.ofTypeVariable(Comparable[], 'values', 'T', annotated, [Argument.of(String, 'T')] as Argument[], bounds)

        when:
        Argument<?> copy = array.withTypeParameters(Argument.of(Integer, 'T'))

        then:
        copy instanceof GenericPlaceholder
        copy.type == Comparable[]
        copy.annotationMetadata.is(annotated)
        ((GenericPlaceholder<?>) copy).variableName == 'T'

        and: 'the component is the variable with the same bounds'
        Argument<?> component = copy.componentType()
        component instanceof GenericPlaceholder
        component.type == Comparable
        ((GenericPlaceholder<?>) component).variableName == 'T'
        ((GenericPlaceholder<?>) component).bounds == bounds.toList()
        component.typeParameters == [Argument.of(Integer, 'T')] as Argument[]
    }

    void "nested type parameters are taken as given, with their own shapes"() {
        given: 'Map<String, List<? extends Number>>'
        Argument<?> wildcard = Argument.ofWildcard(Number, 'E', null, null, [Argument.of(Number)] as Argument[], null)
        Argument<?> map = Argument.of(Map, 'map', annotated,
                Argument.of(String, 'K'),
                Argument.of(List, 'V', wildcard))

        when: 'the list is given ? super Integer'
        Argument<?> list = map.typeParameters[1]
        Argument<?> lower = list.typeParameters[0]
        Argument<?> superInteger = Argument.ofWildcard(Object, 'E', null, null, null, [Argument.of(Integer)] as Argument[])
        Argument<?> copy = map.withTypeParameters(map.typeParameters[0], list.withTypeParameters(superInteger))

        then: 'Map<String, List<? super Integer>>'
        copy.equalsStructure(Argument.of(Map, (String) null, Argument.of(String),
                Argument.of(List, (String) null, Argument.ofWildcard(Object, null, null, null, null, [Argument.of(Integer)] as Argument[]))))
        copy.typeParameters[1].name == 'V'
        copy.typeParameters[1].typeParameters[0] instanceof WildcardArgument
        ((WildcardArgument<?>) copy.typeParameters[1].typeParameters[0]).lowerBounds == [Argument.of(Integer)]

        and: 'the original is untouched'
        map.typeParameters[1].typeParameters[0].is(lower)
        !copy.equalsStructure(map)
    }

    @Unroll
    void "a copy with the same type parameters equals the original: #description"() {
        when:
        Argument<?> copy = argument.withTypeParameters(argument.typeParameters)

        then:
        !copy.is(argument)
        copy == argument
        argument == copy
        copy.hashCode() == argument.hashCode()
        copy.equalsType(argument)
        copy.typeHashCode() == argument.typeHashCode()
        copy.equalsStructure(argument)
        copy.structureHashCode() == argument.structureHashCode()
        copy.rawType == argument.rawType
        copy.typeVariable == argument.typeVariable
        copy.annotationMetadata.is(argument.annotationMetadata)
        copy.toString() == argument.toString()

        where:
        description            | argument
        'plain'                | Argument.of(Map, 'map', annotated, Argument.of(String, 'K'), Argument.of(Integer, 'V'))
        'unnamed'              | Argument.of(List, Argument.of(String))
        'no type parameters'   | Argument.of(String, 'name', annotated)
        'raw'                  | Argument.ofRawType(List, 'list', annotated, [Argument.of(Object, 'E')] as Argument[])
        'wildcard'             | Argument.ofWildcard(Comparable, 'E', annotated, [Argument.of(String, 'T')] as Argument[], [Argument.of(Comparable, 'T', Argument.of(String, 'T'))] as Argument[], [Argument.of(CharSequence)] as Argument[])
        'type variable'        | Argument.ofTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(Number, 'T')] as Argument[], [Argument.of(Comparable, 'T', Argument.of(Number, 'T'))] as Argument[])
        'resolved variable'    | Argument.ofResolvedTypeVariable(Integer, 'value', 'X', annotated, null, [Argument.of(Number)] as Argument[])
        'array'                | Argument.of(List[], 'lists', annotated, Argument.of(String, 'E'))
        'array of a variable'  | Argument.ofTypeVariable(Number[], 'values', 'T', annotated, null, [Argument.of(Number)] as Argument[])
    }

    @Unroll
    void "the annotation metadata is replaced together with the type parameters: #description"() {
        given:
        AnnotationMetadata other = new AnnotationMetadata() {
            @Override boolean hasAnnotation(String annotation) { false }
        }

        when:
        Argument<?> copy = argument.withAnnotationMetadata(other).withTypeParameters(Argument.of(Long, 'T'))

        then:
        copy.annotationMetadata.is(other)
        copy.class == argument.class
        copy.name == argument.name
        copy.typeParameters == [Argument.of(Long, 'T')] as Argument[]

        and: 'the other way round'
        Argument<?> reversed = argument.withTypeParameters(Argument.of(Long, 'T')).withAnnotationMetadata(other)
        reversed == copy
        reversed.class == copy.class

        where:
        description     | argument
        'plain'         | Argument.of(Comparable, 'value', annotated, Argument.of(String, 'T'))
        'raw'           | Argument.ofRawType(Comparable, 'value', annotated, [Argument.of(Object, 'T')] as Argument[])
        'wildcard'      | Argument.ofWildcard(Comparable, 'T', annotated, [Argument.of(String, 'T')] as Argument[], null, null)
        'type variable' | Argument.ofTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(String, 'T')] as Argument[], null)
    }

    @Unroll
    void "an argument that only implements the interface gets the shape from it: #description"() {
        given:
        Argument<?> forwarding = forward(argument)

        when:
        Argument<?> copy = forwarding.withTypeParameters(Argument.of(Long, 'T'))

        then:
        copy.class == argument.class
        copy.name == argument.name
        copy.annotationMetadata.is(argument.annotationMetadata)
        copy.rawType == argument.rawType
        copy.typeParameters == [Argument.of(Long, 'T')] as Argument[]
        copy == argument.withTypeParameters(Argument.of(Long, 'T'))
        (copy instanceof WildcardArgument) == (argument instanceof WildcardArgument)
        if (argument instanceof WildcardArgument) {
            assert ((WildcardArgument<?>) copy).upperBounds == ((WildcardArgument<?>) argument).upperBounds
            assert ((WildcardArgument<?>) copy).lowerBounds == ((WildcardArgument<?>) argument).lowerBounds
        } else if (argument instanceof GenericPlaceholder) {
            assert ((GenericPlaceholder<?>) copy).variableName == ((GenericPlaceholder<?>) argument).variableName
            assert ((GenericPlaceholder<?>) copy).bounds == ((GenericPlaceholder<?>) argument).bounds
            assert ((GenericPlaceholder<?>) copy).resolved == ((GenericPlaceholder<?>) argument).resolved
        }

        where:
        description         | argument
        'plain'             | Argument.of(Comparable, 'value', annotated, Argument.of(String, 'T'))
        'raw'               | Argument.ofRawType(Comparable, 'value', annotated, [Argument.of(Object, 'T')] as Argument[])
        'wildcard'          | Argument.ofWildcard(Comparable, 'T', annotated, [Argument.of(String, 'T')] as Argument[], [Argument.of(Comparable)] as Argument[], [Argument.of(String)] as Argument[])
        'type variable'     | Argument.ofTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(String, 'T')] as Argument[], [Argument.of(Comparable)] as Argument[])
        'resolved variable' | Argument.ofResolvedTypeVariable(Comparable, 'value', 'X', annotated, [Argument.of(String, 'T')] as Argument[], [Argument.of(Comparable)] as Argument[])
    }

    /**
     * An argument of the given shape that implements the interfaces itself, so that only their default methods
     * answer what is not forwarded.
     */
    private static Argument<?> forward(Argument<?> argument) {
        if (argument instanceof WildcardArgument<?>) {
            return new ForwardingWildcard(argument)
        }
        if (argument instanceof GenericPlaceholder<?>) {
            return new ForwardingPlaceholder(argument)
        }
        return new ForwardingArgument(argument)
    }

    private static class ForwardingArgument implements Argument<Object> {
        final Argument<Object> target

        ForwardingArgument(Argument<?> target) {
            this.target = (Argument<Object>) target
        }

        @Override Class<Object> getType() { target.type }
        @Override String getName() { target.name }
        @Override Map<String, Argument<?>> getTypeVariables() { target.typeVariables }
        @Override Argument<?>[] getTypeParameters() { target.typeParameters }
        @Override AnnotationMetadata getAnnotationMetadata() { target.annotationMetadata }
        @Override boolean isRawType() { target.rawType }
        @Override boolean isTypeVariable() { target.typeVariable }
        @Override boolean equalsType(Argument<?> other) { target.equalsType(other) }
        @Override int typeHashCode() { target.typeHashCode() }
    }

    private static class ForwardingPlaceholder extends ForwardingArgument implements GenericPlaceholder<Object> {
        ForwardingPlaceholder(Argument<?> target) {
            super(target)
        }

        @Override String getVariableName() { ((GenericPlaceholder<?>) target).variableName }
        @Override List<Argument<?>> getBounds() { ((GenericPlaceholder<?>) target).bounds }
        @Override boolean isResolved() { ((GenericPlaceholder<?>) target).resolved }
    }

    private static class ForwardingWildcard extends ForwardingPlaceholder implements WildcardArgument<Object> {
        ForwardingWildcard(Argument<?> target) {
            super(target)
        }

        @Override List<Argument<?>> getUpperBounds() { ((WildcardArgument<?>) target).upperBounds }
        @Override List<Argument<?>> getLowerBounds() { ((WildcardArgument<?>) target).lowerBounds }
    }
}
