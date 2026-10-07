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
 * A placeholder says whether it stands for its type variable or for a type resolved in place of the variable,
 * without changing anything else it answers.
 */
class ResolvedTypeVariableSpec extends Specification {

    void "a placeholder built for a variable is not resolved"() {
        expect:
        !((GenericPlaceholder<?>) Argument.ofTypeVariable(Number, 'T')).resolved
        !((GenericPlaceholder<?>) Argument.ofTypeVariable(Number, 'E', 'T')).resolved
        !((GenericPlaceholder<?>) Argument.ofTypeVariable(Number, 'E', 'T', null, null, null)).resolved
    }

    void "a placeholder built for a type resolved in place of a variable says so, and keeps the variable"() {
        given:
        GenericPlaceholder<?> resolved = (GenericPlaceholder<?>) Argument.ofResolvedTypeVariable(
                Integer, 'E', 'X', null, null, [Argument.of(Number)] as Argument[])

        expect:
        resolved.resolved
        resolved.type == Integer
        resolved.name == 'E'
        resolved.variableName == 'X'
        resolved.bounds*.type == [Number]

        and: 'it is still a placeholder of the variable, the way it was compiled before'
        resolved.isTypeVariable()

        and: 'and the flag does not take part in equality'
        resolved == Argument.ofTypeVariable(Integer, 'E', 'X')
        resolved.equalsType(Argument.ofTypeVariable(Integer, 'E', 'X'))
        resolved.typeHashCode() == Argument.ofTypeVariable(Integer, 'E', 'X').typeHashCode()
    }

    void "renaming a resolved placeholder, or giving it other metadata, keeps it resolved"() {
        given:
        Argument<?> resolved = Argument.ofResolvedTypeVariable(Integer, 'E', 'X', null, null, null)

        expect:
        ((GenericPlaceholder<?>) resolved.withName('other')).resolved
        ((GenericPlaceholder<?>) resolved.withName('other')).variableName == 'X'
        ((GenericPlaceholder<?>) resolved.withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA)).resolved
    }

    void "the placeholders built from the classes of the type arguments are types resolved in place of the variables"() {
        given:
        Argument<?> argument = Argument.of(Map, AnnotationMetadata.EMPTY_METADATA, [String, Integer] as Class[])

        expect: 'they are the placeholders they always were'
        argument.typeParameters*.name == ['K', 'V']
        argument.typeParameters*.type == [String, Integer]
        argument.typeParameters.every { it instanceof GenericPlaceholder && it.isTypeVariable() }

        and: 'which say they hold the classes they were given rather than the variables'
        argument.typeParameters.every { ((GenericPlaceholder<?>) it).resolved }
    }

    void "a placeholder of an array of a variable is bounded by what the variable erases to"() {
        given:
        GenericPlaceholder<?> array = (GenericPlaceholder<?>) Argument.ofTypeVariable(Number[], 'values', 'T')
        GenericPlaceholder<?> twoDimensions = (GenericPlaceholder<?>) Argument.ofTypeVariable(Number[][], 'values', 'T')

        expect:
        array.type == Number[]
        array.bounds*.type == [Number]
        twoDimensions.bounds*.type == [Number]
    }

    void "a placeholder built from a class records no bounds of the variable, and answers the class"() {
        given:
        GenericPlaceholder<?> variable = (GenericPlaceholder<?>) Argument.of(List, AnnotationMetadata.EMPTY_METADATA, [String] as Class[]).typeParameters[0]

        expect:
        variable.resolved
        variable.variableName == 'E'
        variable.bounds*.type == [String]
    }
}
