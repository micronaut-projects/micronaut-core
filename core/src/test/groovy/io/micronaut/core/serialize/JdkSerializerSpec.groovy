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
package io.micronaut.core.serialize

import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JdkSerializerSpec extends Specification {

    void 'test serialize object'() {
        when:
        def bytes = ObjectSerializer.JDK.serialize(new Foo(name: "test")).get()
        Foo foo = ObjectSerializer.JDK.deserialize(bytes, Foo).get()

        then:
        foo.name == "test"
    }

    void 'test serialize null'() {
        when:
        def bytes = ObjectSerializer.JDK.serialize(null).get()
        Optional<Foo> foo = ObjectSerializer.JDK.deserialize(bytes, Foo)

        then:
        !foo.isPresent()
    }

    void 'a class only the thread context loader can see is resolved when the required type cannot see it'() {
        given: "a serializable class defined by a loader below the one of the required type"
        GroovyClassLoader child = new GroovyClassLoader(getClass().classLoader)
        Class<?> hidden = child.parseClass('''
            package example
            class HiddenNamed implements io.micronaut.core.naming.Named, Serializable {
                String name
            }
        ''')
        def instance = hidden.getDeclaredConstructor().newInstance()
        instance.name = "hidden"
        byte[] bytes = ObjectSerializer.JDK.serialize(instance).get()
        ClassLoader previous = Thread.currentThread().contextClassLoader

        when: "the required type is a core interface whose loader cannot see the class"
        Thread.currentThread().contextClassLoader = child
        def result = ObjectSerializer.JDK.deserialize(bytes, io.micronaut.core.naming.Named).get()

        then:
        result.name == "hidden"
        result.getClass() == hidden

        cleanup:
        Thread.currentThread().contextClassLoader = previous
        child.close()
    }

    static class Foo implements Serializable {
        String name
    }
}
