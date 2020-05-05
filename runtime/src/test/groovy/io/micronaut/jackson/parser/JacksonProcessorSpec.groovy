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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.LongNode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext

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
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())
        BigDecimal dec = new BigDecimal("888.7794538169553400000")
        BigD bigD = new BigD(bd1: dec, bd2: dec)

        when:
        def string = objectMapper.writeValueAsString(bigD)
        byte[] bytes = objectMapper.writeValueAsBytes(bigD)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'

        when:
        BigD foo = objectMapper.treeToValue(node, BigD)

        then:
        foo != null
        foo.bd1 == dec
        foo.bd2 == new BigDecimal("888.7794538169553")
    }

    void "test big decimal - USE_BIG_DECIMAL_FOR_FLOATS"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        DeserializationConfig cfg = objectMapper.getDeserializationConfig()
        JacksonProcessor processor = new JacksonProcessor(cfg.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
        BigDecimal dec = new BigDecimal("888.7794538169553400000")
        BigD bigD = new BigD(bd1: dec, bd2: dec)

        when:
        def string = objectMapper.writeValueAsString(bigD)
        byte[] bytes = objectMapper.writeValueAsBytes(bigD)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'

        when:
        BigD foo = objectMapper.treeToValue(node, BigD)

        then:
        foo != null
        foo.bd1 == dec
        foo.bd2 == dec
    }

    void "test big decimal - USE_BIG_DECIMAL_FOR_FLOATS and withExactBigDecimals"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper).setNodeFactory(JsonNodeFactory.withExactBigDecimals(false))
        DeserializationConfig cfg = objectMapper.getDeserializationConfig().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        objectMapper.setConfig(cfg)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getFactory(), cfg)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")
        BigD bigD = new BigD(bd1: dec, bd2: dec)

        when:
        def string = objectMapper.writeValueAsString(bigD)
        byte[] bytes = objectMapper.writeValueAsBytes(bigD)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'

        when:
        BigD foo = objectMapper.treeToValue(node, BigD)

        then:
        foo != null
        foo.bd1.toPlainString() == dec.toPlainString()
        foo.bd2.toPlainString() != dec.toPlainString()
        foo.bd2.toPlainString() == "888.77945381695534"
    }

    void "test big integer"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())
        BigInteger bInt = new BigInteger("9223372036854775807")
        BigI bigI = new BigI(bi1: bInt, bi2: bInt)

        when:
        def string = objectMapper.writeValueAsString(bigI)
        byte[] bytes = objectMapper.writeValueAsBytes(bigI)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bi1":"9223372036854775807","bi2":9223372036854775807}'

        when:
        BigI fooI = objectMapper.treeToValue(node, BigI)
        NumsOSN fooOSN = objectMapper.treeToValue(node, NumsOSN)
        NumsOSBI fooOSBI = objectMapper.treeToValue(node, NumsOSBI)
        NumsN fooN = objectMapper.treeToValue(node, NumsN)

        then:
        fooI != null
        fooI.bi1 == bInt
        fooI.bi2 == bInt
        fooOSN != null
        fooOSN.bi1 == bInt
        fooOSN.bi2 == bInt
        fooOSN.bi1.class == Long
        fooOSN.bi2.class == Long
        fooOSBI != null
        fooOSBI.bi1 == bInt
        fooOSBI.bi2 == bInt
        fooOSBI.bi1.class == BigInteger
        fooOSBI.bi2.class == Long
        fooN != null
        fooN.bi1 == bInt
        fooN.bi2 == bInt
        fooN.bi1.class == Long
        fooN.bi2.class == Long
    }

    void "test big integer - USE_BIG_INTEGER_FOR_INTS"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        DeserializationConfig cfg = objectMapper.getDeserializationConfig()
        JacksonProcessor processor = new JacksonProcessor(cfg.with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS))
        JacksonProcessor processor2 = new JacksonProcessor(cfg.with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS))
        BigInteger bInt = new BigInteger("9223372036854775807")
        BigI bigI = new BigI(bi1: bInt, bi2: bInt)

        when:
        def string = objectMapper.writeValueAsString(bigI)
        byte[] bytes = objectMapper.writeValueAsBytes(bigI)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bi1":"9223372036854775807","bi2":9223372036854775807}'

        when:
        BigI fooI = objectMapper.treeToValue(node, BigI)
        NumsO numsO = objectMapper.treeToValue(node, NumsO)
        NumsOSN numsOSN = objectMapper.treeToValue(node, NumsOSN)
        NumsOSBI numsOSBI = objectMapper.treeToValue(node, NumsOSBI)
        NumsN numsN = objectMapper.treeToValue(node, NumsN)

        then:
        fooI != null
        fooI.bi1 == bInt
        fooI.bi2 == bInt
        numsO != null
        numsO.bi1 == "9223372036854775807"
        numsO.bi2 == bInt
        numsO.bi1.class == String
        numsO.bi2.class == BigInteger
        numsOSN != null
        numsOSN.bi1 == bInt
        numsOSN.bi2 == bInt
        numsOSN.bi1.class == Long
        numsOSN.bi2.class == BigInteger
        numsOSBI != null
        numsOSBI.bi1 == bInt
        numsOSBI.bi2 == bInt
        numsOSBI.bi1.class == BigInteger
        numsOSBI.bi2.class == BigInteger
        numsN != null
        numsN.bi1 == bInt
        numsN.bi2 == bInt
        numsN.bi1.class == Long
        numsN.bi2.class == BigInteger

    }

    void "test big integer without string - USE_BIG_INTEGER_FOR_INTS"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        DeserializationConfig cfg = objectMapper.getDeserializationConfig()
        JacksonProcessor processor = new JacksonProcessor(cfg.with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS))
        BigInteger bInt = new BigInteger("9223372036854775807")
        NumsO bigI = new NumsO(bi1: bInt, bi2: bInt)

        when:
        def string = objectMapper.writeValueAsString(bigI)
        byte[] bytes = objectMapper.writeValueAsBytes(bigI)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        string == '{"bi1":9223372036854775807,"bi2":9223372036854775807}'

        when:
        BigI fooI = objectMapper.treeToValue(node, BigI)
        NumsO numsO = objectMapper.treeToValue(node, NumsO)
        NumsOSN numsOSN = objectMapper.treeToValue(node, NumsOSN)
        NumsOSBI numsOSBI = objectMapper.treeToValue(node, NumsOSBI)
        NumsN numsN = objectMapper.treeToValue(node, NumsN)

        then:
        fooI != null
        fooI.bi1 == bInt
        fooI.bi2 == bInt
        numsO != null
        numsO.bi1 == bInt
        numsO.bi2 == bInt
        numsO.bi1.class == BigInteger
        numsO.bi2.class == BigInteger
        numsOSN != null
        numsOSN.bi1 == bInt
        numsOSN.bi2 == bInt
        numsOSN.bi1.class == BigInteger
        numsOSN.bi2.class == BigInteger
        numsOSBI != null
        numsOSBI.bi1 == bInt
        numsOSBI.bi2 == bInt
        numsOSBI.bi1.class == BigInteger
        numsOSBI.bi2.class == BigInteger
        numsN != null
        numsN.bi1 == bInt
        numsN.bi2 == bInt
        numsN.bi1.class == BigInteger
        numsN.bi2.class == BigInteger
    }

    void "test publish JSON node async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())
        Foo instance = new Foo(name: "Fred", age: 10)


        when:
        def string = objectMapper.writeValueAsString(instance)
        byte[] bytes = objectMapper.writeValueAsBytes(instance)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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

    void "test publish JSON array async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())
        Foo[] instances = [new Foo(name: "Fred", age: 10), new Foo(name: "Barney", age: 11)] as Foo[]


        when:
        def string = objectMapper.writeValueAsString(instances)
        byte[] bytes = objectMapper.writeValueAsBytes(instances)
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node != null
        error == null
        nodeCount == 1
        node instanceof ArrayNode
        string == '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'

        when:
        Foo[] foos = objectMapper.treeToValue(node, Foo[].class)

        then:
        foos.size() == 2
        foos[0] != null
        foos[0].name == "Fred"
        foos[0].age == 10
    }

    void "test incomplete JSON error"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())


        when:
        byte[] bytes = '{"name":"Fred","age":10'.bytes // invalid JSON
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node == null
        error != null
        error instanceof JsonEOFException

    }

    void "test JSON syntax error"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(objectMapper.getDeserializationConfig())


        when:
        byte[] bytes = '{"name":Fred,"age":10}'.bytes // invalid JSON
        boolean complete = false
        JsonNode node = null
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
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
        node == null
        error != null
        error instanceof JsonParseException

    }

    void "test nested arrays"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor(new JsonFactory(), true, objectMapper.getDeserializationConfig())

        when:
        long longValue = Integer.MAX_VALUE + 1L
        BigInteger bigIntegerValue = new BigInteger(Long.MAX_VALUE) + 1L
        BigDecimal bigDecimalValue = new BigDecimal(Double.MAX_VALUE) * new BigDecimal("10")
        byte[] bytes = "[1, $longValue, [3, 4, [5, 6], 7], $bigDecimalValue, [8, 9, 10], 11, $bigIntegerValue]".bytes
        boolean complete = false
        List<JsonNode> nodes = new ArrayList<>()
        Throwable error = null
        int nodeCount = 0
        processor.subscribe(new Subscriber<JsonNode>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(JsonNode jsonNode) {
                nodeCount++
                nodes.add(jsonNode)
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
        nodeCount == 7
        nodes[0].equals(JsonNodeFactory.instance.numberNode(1))
        nodes[1].equals(JsonNodeFactory.instance.numberNode(longValue))
        ((ArrayNode) nodes[2]).size() == 4
        nodes[3].numberValue().toBigDecimal() == bigDecimalValue
        ((ArrayNode) nodes[4]).size() == 3
        nodes[5].equals(JsonNodeFactory.instance.numberNode(11))
        nodes[6].equals(JsonNodeFactory.instance.numberNode(bigIntegerValue))
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

class BigI {
    @JsonFormat(shape= JsonFormat.Shape.STRING)
    BigInteger bi1
    BigInteger bi2
}

class NumsO {
    Object bi1
    Object bi2
}

class NumsOSN {
    @JsonDeserialize(as = Number.class)
    Object bi1
    Object bi2
}

class NumsOSBI {
    @JsonDeserialize(as = BigInteger.class)
    Object bi1
    Object bi2
}

class NumsN {
    Number bi1
    Number bi2
}
