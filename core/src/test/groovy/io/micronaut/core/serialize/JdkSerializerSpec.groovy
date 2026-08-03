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

import io.micronaut.core.serialize.exceptions.SerializationException
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.LocalDate

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

    void 'test reject serialized type outside required type hierarchy'() {
        given:
        DeserializationPayload.deserialized = false
        def bytes = ObjectSerializer.JDK.serialize(new DeserializationPayload()).get()

        when:
        ObjectSerializer.JDK.deserialize(bytes, CharSequence)

        then:
        thrown(SerializationException)
        !DeserializationPayload.deserialized
    }

    void 'test deserialize subtype of required type'() {
        when:
        def bytes = ObjectSerializer.JDK.serialize(new Foo(name: "test")).get()
        def foo = ObjectSerializer.JDK.deserialize(bytes, Serializable).get()

        then:
        foo instanceof Foo
        foo.name == "test"
    }

    void 'test deserialize standard serialization proxy'() {
        given:
        def date = LocalDate.of(2026, 8, 3)

        when:
        def bytes = ObjectSerializer.JDK.serialize(date).get()
        def deserialized = ObjectSerializer.JDK.deserialize(bytes, LocalDate).get()

        then:
        deserialized == date
    }

    void 'test deserialize dynamic proxy'() {
        given:
        def proxy = Proxy.newProxyInstance(
            Greeting.classLoader,
            [Greeting] as Class[],
            new GreetingInvocationHandler()
        )

        when:
        def bytes = ObjectSerializer.JDK.serialize(proxy).get()
        def deserialized = ObjectSerializer.JDK.deserialize(bytes, Greeting).get()

        then:
        deserialized.greet() == "hello"
    }

    void 'test reject incompatible read resolve result'() {
        when:
        def bytes = ObjectSerializer.JDK.serialize(new ReplacingPayload()).get()
        ObjectSerializer.JDK.deserialize(bytes, ReplacingPayload)

        then:
        thrown(SerializationException)
    }

    void 'test preserve inherited serial filter for nested types'() {
        given:
        FilteredPayload.deserialized = false
        def bytes = ObjectSerializer.JDK.serialize(
            new FilteredRoot(payload: new FilteredPayload())
        ).get()
        ObjectInputFilter.Config.setSerialFilter({ filterInfo ->
            filterInfo.serialClass() == FilteredPayload ?
                ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED
        } as ObjectInputFilter)

        when:
        ObjectSerializer.JDK.deserialize(bytes, FilteredRoot)

        then:
        thrown(SerializationException)
        !FilteredPayload.deserialized
    }

    void 'test reject unrelated type before static initialization'() {
        given:
        String payloadClassName = 'io.micronaut.core.serialize.DeserializationInitializationPayload'
        String initializedProperty = 'io.micronaut.core.serialize.initialized'
        URL testClasses = Class.forName(payloadClassName, false, getClass().classLoader)
            .protectionDomain.codeSource.location
        byte[] bytes
        new URLClassLoader([testClasses] as URL[], null).withCloseable { classLoader ->
            def payload = Class.forName(payloadClassName, true, classLoader).getConstructor().newInstance()
            bytes = ObjectSerializer.JDK.serialize(payload).get()
        }
        System.clearProperty(initializedProperty)

        when:
        deserializeWithContextClassLoader(bytes, CharSequence, testClasses)

        then:
        thrown(SerializationException)
        System.getProperty(initializedProperty) == null

        cleanup:
        System.clearProperty(initializedProperty)
    }

    static class Foo implements Serializable {
        String name
    }

    static class DeserializationPayload implements Serializable {
        static boolean deserialized

        private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
            deserialized = true
            inputStream.defaultReadObject()
        }
    }

    static class ReplacingPayload implements Serializable {
        private Object readResolve() {
            return "replacement"
        }
    }

    static class FilteredRoot implements Serializable {
        FilteredPayload payload
    }

    static class FilteredPayload implements Serializable {
        static boolean deserialized

        private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
            deserialized = true
            inputStream.defaultReadObject()
        }
    }

    static interface Greeting extends Serializable {
        String greet()
    }

    static class GreetingInvocationHandler implements InvocationHandler, Serializable {
        @Override
        Object invoke(Object proxy, Method method, Object[] args) {
            return "hello"
        }
    }

    private static void deserializeWithContextClassLoader(byte[] bytes, Class<?> requiredType, URL testClasses) {
        Thread currentThread = Thread.currentThread()
        ClassLoader previousClassLoader = currentThread.contextClassLoader
        try {
            new URLClassLoader([testClasses] as URL[], null).withCloseable { classLoader ->
                currentThread.contextClassLoader = classLoader
                ObjectSerializer.JDK.deserialize(bytes, requiredType)
            }
        } finally {
            currentThread.contextClassLoader = previousClassLoader
        }
    }
}
