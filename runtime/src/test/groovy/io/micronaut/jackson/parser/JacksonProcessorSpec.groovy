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
package io.micronaut.jackson.parser

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.io.JsonEOFException
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.util.TokenBuffer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.runtime.server.EmbeddedServer

import java.math.BigDecimal

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JacksonProcessorSpec extends Specification {
    @Shared @AutoCleanup
    ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

    void "test big decimal"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")
        BigD bigD = new BigD(bd1: dec, bd2: dec)

        when:
        def string = objectMapper.writeValueAsString(bigD)
        byte[] bytes = objectMapper.writeValueAsBytes(bigD)
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()

        then:
        complete
        tokenBuffer != null
        error == null
        nodeCount == 1
        string == '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'

        when:
        BigD foo = objectMapper.readValue(tokenBuffer.asParser(objectMapper), BigD)

        then:
        foo != null
        foo.bd1 == dec
        foo.bd2 == new BigDecimal("888.7794538169553")
    }

    void "test big decimal - USE_BIG_DECIMAL_FOR_FLOATS"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")
        BigD bigD = new BigD(bd1: dec, bd2: dec)

        when:
        def string = objectMapper.writeValueAsString(bigD)
        byte[] bytes = objectMapper.writeValueAsBytes(bigD)
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()

        then:
        complete
        tokenBuffer != null
        error == null
        nodeCount == 1
        string == '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'

        when:
        BigD foo = objectMapper.readValue(tokenBuffer.asParser(objectMapper), BigD)

        then:
        foo != null
        foo.bd1 == dec
        foo.bd2 == dec
    }

    void "test publish JSON node async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)
        Foo instance = new Foo(name: "Fred", age: 10)


        when:
        def string = objectMapper.writeValueAsString(instance)
        byte[] bytes = objectMapper.writeValueAsBytes(instance)
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()

        then:
        complete
        tokenBuffer != null
        error == null
        nodeCount == 1
        string == '{"name":"Fred","age":10}'

        when:
        Foo foo = objectMapper.readValue(tokenBuffer.asParser(objectMapper), Foo)

        then:
        foo != null
        foo.name == "Fred"
        foo.age == 10
    }

    void "test publish JSON array async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)
        Foo[] instances = [new Foo(name: "Fred", age: 10), new Foo(name: "Barney", age: 11)] as Foo[]


        when:
        def string = objectMapper.writeValueAsString(instances)
        byte[] bytes = objectMapper.writeValueAsBytes(instances)
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()

        then:
        complete
        tokenBuffer != null
        error == null
        nodeCount == 1
        string == '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'

        when:
        Foo[] foos = objectMapper.readValue(tokenBuffer.asParser(objectMapper), Foo[].class)

        then:
        foos.size() == 2
        foos[0] != null
        foos[0].name == "Fred"
        foos[0].age == 10
    }

    void "test incomplete JSON error"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)


        when:
        byte[] bytes = '{"name":"Fred","age":10'.bytes // invalid JSON
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()
        then:
        !complete
        tokenBuffer == null
        error != null
        error instanceof JsonEOFException

    }

    void "test JSON syntax error"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper)


        when:
        byte[] bytes = '{"name":Fred,"age":10}'.bytes // invalid JSON
        boolean complete = false
        TokenBuffer tokenBuffer = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tb) {
                nodeCount++
                tokenBuffer = tb
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()
        then:
        !complete
        tokenBuffer == null
        error != null
        error instanceof JsonParseException

    }

    void "test nested arrays"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(new JsonFactory(), true, objectMapper)

        when:
        byte[] bytes = '[1, 2, [3, 4, [5, 6], 7], [8, 9, 10], 11, 12]'.bytes
        boolean complete = false
        List<JsonNode> nodes = new ArrayList<>()
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<TokenBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(TokenBuffer tokenBuffer) {
                nodeCount++
                nodes.add(objectMapper.readTree(tokenBuffer.asParser()))
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
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {

            }

            @Override
            void cancel() {

            }
        })
        processor.onNext(bytes)
        processor.onComplete()

        then:
        nodeCount == 6
        nodes[0].equals(JsonNodeFactory.instance.numberNode(1))
        nodes[1].equals(JsonNodeFactory.instance.numberNode(2))
        ((ArrayNode) nodes[2]).size() == 4
        ((ArrayNode) nodes[3]).size() == 3
        nodes[4].equals(JsonNodeFactory.instance.numberNode(11))
        nodes[5].equals(JsonNodeFactory.instance.numberNode(12))
    }

}

class BigD {
    @JsonFormat(shape= JsonFormat.Shape.STRING)
    BigDecimal bd1
    BigDecimal bd2
}

class Foo {
    String name
    Integer age
}
