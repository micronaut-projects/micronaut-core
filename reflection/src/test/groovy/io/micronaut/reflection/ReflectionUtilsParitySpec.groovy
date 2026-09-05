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

import io.micronaut.core.reflect.ReflectionUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The method lookup of {@link ReflectionUtils}, next to {@link ReflectionExecutables#findMethod}.
 *
 * <p>{@code ReflectionUtils} is not going anywhere: {@code getRequiredMethod}, {@code invokeInaccessibleMethod},
 * {@code getField} and {@code EMPTY_CLASS_ARRAY} are {@link io.micronaut.core.annotation.UsedByGeneratedCode},
 * so bytecode written by every release so far calls them by their exact descriptor, and neither deprecating them
 * nor changing what they do is open to us. This says what the two answer, so that a caller writing new reflective
 * code knows which one it wants; generated code keeps calling the one it calls.</p>
 *
 * <p>{@code ReflectionUtils.findMethod} matches the parameter types exactly and takes the first declaration
 * {@code getDeclaredMethods} happens to report; it reads the interfaces only of the type it is asked about, not
 * those a super class implements. {@link ReflectionExecutables#findMethod} answers the same for every method the
 * former finds, and answers as well where it does not - which is what a caller handed an arbitrary class, rather
 * than a descriptor a processor wrote, needs.</p>
 *
 * <p>The field accessors of {@code ReflectionUtils} - {@code findField}, {@code getRequiredField},
 * {@code setField} - have no counterpart in this module, which reads a field through the introspection of the
 * type that declares it, so the rest of {@code ReflectionUtilsSpec} has nothing to be asked of here.</p>
 */
class ReflectionUtilsParitySpec extends Specification {

    @Unroll
    void 'the method #name#parameterTypes is found the same way'() {
        expect:
        ReflectionExecutables.findMethod(Sub, name, parameterTypes as Class[]) ==
                ReflectionUtils.findMethod(Sub, name, parameterTypes as Class[])

        and:
        ReflectionExecutables.findMethod(Sub, name, parameterTypes as Class[]).isPresent() == found

        where:
        name       | parameterTypes | found
        "declared" | [String]       | true
        "declared" | [Integer]      | false
        "inherited"| [String]       | true
        "statik"   | []             | true
        "absent"   | []             | false
    }

    void 'a method of an interface a super class implements is found, where ReflectionUtils reads no interface'() {
        expect:
        ReflectionExecutables.findMethod(Sub, "fromInterface").get() == Contract.getDeclaredMethod("fromInterface")

        and:
        ReflectionUtils.findMethod(Sub, "fromInterface").isEmpty()
    }

    void 'the erased declaration of a generic method is found, where ReflectionUtils matches exactly'() {
        expect: "the bridge reports String, the declaration Object"
        ReflectionExecutables.findMethod(Sub, "generic", String).get() == Base.getDeclaredMethod("generic", Object)

        and:
        ReflectionUtils.findMethod(Sub, "generic", String).isEmpty()
    }

    void 'getRequiredMethod agrees, and the miss is a NoSuchMethodError either way'() {
        expect:
        ReflectionExecutables.findMethod(Sub, "declared", String).get() ==
                ReflectionUtils.getRequiredMethod(Sub, "declared", String)

        when:
        ReflectionUtils.getRequiredMethod(Sub, "absent")

        then:
        thrown(NoSuchMethodError)

        and:
        ReflectionExecutables.findMethod(Sub, "absent").isEmpty()
    }

    interface Contract {
        default void fromInterface() {
        }
    }

    static class Base<T> implements Contract {
        void inherited(String value) {
        }

        void generic(T value) {
        }
    }

    static class Sub extends Base<String> {
        void declared(String value) {
        }

        static void statik() {
        }
    }
}
