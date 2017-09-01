/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.jackson.parser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JacksonPublisherSpec extends Specification {
    @Shared @AutoCleanup
    ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

    void "test publish JSON node async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonPublisher publisher = new JacksonPublisher()
        Foo instance = new Foo(name: "Fred", age: 10)


        when:
        def string = objectMapper.writeValueAsString(instance)
        byte[] bytes = objectMapper.writeValueAsBytes(instance)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        publisher.subscribe(new Subscriber<JsonNode>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(JsonNode jsonNode) {
                nodeCount++
                node = jsonNode
            }

            @Override
            void onError(Throwable t) {
                error = t
            }

            @Override
            void onComplete() {
                complete = true
            }
        })
        publisher.onNext(bytes)

        then:
        complete
        node != null
        error == null
        nodeCount == 1
        string == '{"name":"Fred","age":10}'

        when:
        Foo foo = objectMapper.treeToValue(node, Foo)

        then:
        foo != null
        foo.name == "Fred"
        foo.age == 10
    }

}

class Foo {
    String name
    Integer age
}
