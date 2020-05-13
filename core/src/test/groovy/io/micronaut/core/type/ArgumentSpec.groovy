/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.type

import spock.lang.Specification

import java.lang.reflect.ParameterizedType

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ArgumentSpec extends Specification {

    void "test as parameterized type"() {
        given:
        def listOfString = Argument.listOf(String)
        def parameterizedType = listOfString.asParameterizedType()

        expect:
        parameterizedType.actualTypeArguments[0].typeName == String.name
        parameterizedType.rawType.typeName == List.name
        parameterizedType.typeName == 'java.util.List<java.lang.String>'
    }

    void "test equals/hashcode"() {
        expect:
        Argument.of(Optional.class, Integer.class).hashCode() == Argument.of(Optional.class, Integer.class).hashCode()
        Argument.of(Optional.class, Integer.class) == Argument.of(Optional.class, Integer.class)
    }

    void "test generic list"() {
        def arg = new GenericArgument<List<String>>() {}
        expect:
        arg.getType() == List.class
        arg.getTypeParameters().length == 1
        arg.getTypeParameters()[0].getType() == String.class
        arg == Argument.listOf(String.class)
    }

    void "test generic set"() {
        def arg = new GenericArgument<Set<String>>() {}
        expect:
        arg.getType() == Set.class
        arg.getTypeParameters().length == 1
        arg.getTypeParameters()[0].getType() == String.class
        arg == Argument.setOf(String.class)
    }

    void "test generic map"() {
        def arg = new GenericArgument<Map<UUID, String>>() {}
        expect:
        arg.getType() == Map.class
        arg.getTypeParameters().length == 2
        arg.getTypeParameters()[0].getType() == UUID.class
        arg.getTypeParameters()[1].getType() == String.class
        arg == Argument.mapOf(UUID, String)
    }

    void "test generic list of lists"() {
        def arg = new GenericArgument<List<List<Long>>>() {}
        expect:
        arg.getType() == List.class
        arg.getTypeParameters()[0].getType() == List.class
        arg.getTypeParameters()[0].getTypeParameters()[0].getType() == Long.class
    }

/*
    void "test inner class"() {
        def arg = new Test<String>() {}.get();
        expect:
        arg.getType() == List.class
        arg.getTypeParameters()[0].getType() == String.class
    }

    abstract class Test<T> {
        Argument<List<T>> get() {
            return new GenericArgument<List<T>>(getClass()) {}
        }
    }
*/
}
