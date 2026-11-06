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
package io.micronaut.core.reflect

import io.micronaut.core.reflect.exception.InvocationException
import spock.lang.Specification

import java.lang.reflect.Field

class ReflectionUtilsSpec extends Specification {

    void "test invokeMethodPropagating returns the result"() {
        expect:
        ReflectionUtils.invokeMethodPropagating(null, Throwing.getDeclaredMethod('echo', String), 'hello') == 'hello'
    }

    void "test invokeMethodPropagating throws the checked exception of the method unwrapped"() {
        when:
        ReflectionUtils.invokeMethodPropagating(null, Throwing.getDeclaredMethod('fail'))

        then:
        IOException e = thrown()
        e.message == 'fail'
    }

    void "test invokeMethodPropagating wraps an illegal access"() {
        when:
        ReflectionUtils.invokeMethodPropagating(null, Throwing.getDeclaredMethod('hidden'))

        then:
        InvocationException e = thrown()
        e.cause instanceof IllegalAccessException
    }

    void "test findField"() {
        given:
        Optional<Field> field = ReflectionUtils.findField(ArrayList, fieldName)

        expect:
        result == field.isPresent()

        where:
        fieldName            | result
        "size"               | true
        "noField"            | false
    }

    void "test set field"() {
        given:
        def f = ReflectionUtils.getRequiredField(Foo, "bar")
        def foo = new Foo()

        when:
        ReflectionUtils.setField(f, foo, "test")

        then:
        foo.bar == 'test'
    }

    class Foo {
        String bar
    }

    static class Throwing {
        static String echo(String value) {
            return value
        }

        static String fail() throws IOException {
            throw new IOException('fail')
        }

        private static String hidden() {
            return 'hidden'
        }
    }
}
