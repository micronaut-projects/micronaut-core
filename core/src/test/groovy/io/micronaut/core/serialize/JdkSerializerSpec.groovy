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

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.serialize.exceptions.SerializationException
import spock.lang.Specification

import java.io.ObjectInputFilter

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

    void 'test deserialization is rejected when an ObjectInputFilter disallows the class'() {
        given:
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter('java.lang.*;java.util.*;!*')
        def serializer = new JdkSerializer(ConversionService.SHARED, filter)
        def bytes = serializer.serialize(new Foo(name: "test")).get()

        when:
        serializer.deserialize(bytes, Foo)

        then:
        thrown(SerializationException)
    }

    void 'test deserialization succeeds when an ObjectInputFilter allows the required type'() {
        given:
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter('io.micronaut.core.serialize.JdkSerializerSpec$Foo;java.lang.*;java.util.*;!*')
        def serializer = new JdkSerializer(ConversionService.SHARED, filter)
        def bytes = serializer.serialize(new Foo(name: "test")).get()

        when:
        Foo foo = serializer.deserialize(bytes, Foo).get()

        then:
        foo.name == "test"
    }

    static class Foo implements Serializable {
        String name
    }
}
