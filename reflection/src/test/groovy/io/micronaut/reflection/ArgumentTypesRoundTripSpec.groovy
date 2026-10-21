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
package io.micronaut.reflection

import io.micronaut.core.type.ArgumentTypes
import spock.lang.Specification

import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * A type read reflectively into an argument renders back through core's {@link io.micronaut.core.type.Argument#asType()},
 * which {@link ReflectionArguments#toExactType} delegates to, as the type it was read from.
 */
class ArgumentTypesRoundTripSpec extends Specification {

    private static Type typeOf(String field) {
        RoundTripTypes.getDeclaredField(field).genericType
    }

    void "a type read exactly renders back equal to the type read"() {
        given:
        def reflected = typeOf(field)
        def rendered = ReflectionArguments.ofExact(reflected).asType()

        expect:
        rendered == reflected
        reflected == rendered
        rendered.hashCode() == reflected.hashCode()
        rendered.typeName == reflected.typeName

        where:
        field << ['extendsNumber', 'number', 'superBook', 'unbounded', 'stringMaps', 'matrix', 'rawLists', 'raw', 'nested']
    }

    void "a type with a variable renders back with the same name and shape"() {
        given:
        def reflected = typeOf(field)
        def rendered = ReflectionArguments.ofExact(reflected).asType()

        expect:
        rendered.typeName == reflected.typeName

        where:
        field << ['variableMaps', 'keys']
    }

    void "the reflection module renders through core"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf(field))

        expect:
        ReflectionArguments.toExactType(argument) == argument.asType()
        ReflectionArguments.toExactType(argument).getClass() == argument.asType().getClass()

        where:
        field << ['extendsNumber', 'stringMaps', 'keys', 'nested']
    }

    void "a variable rendered by the reflection module is the one core renders"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf('keys')).componentType()

        expect:
        ReflectionArguments.toType(argument) instanceof TypeVariable
        ReflectionArguments.toType(argument).getClass() == ArgumentTypes.typeVariable('K').getClass()
    }
}
