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
package io.micronaut.jackson.core.parser


import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.io.JsonEOFException
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.json.JsonStreamConfig
import io.micronaut.json.tree.JsonNode
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Adapted from JacksonProcessorSpec
 */
class JacksonCoreProcessorSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

    void "test big decimal"() {

        given:
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")

        when:
        def string = '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test big decimal - USE_BIG_DECIMAL_FOR_FLOATS"() {

        given:
        def cfg = JsonStreamConfig.DEFAULT.withUseBigDecimalForFloats(true)
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), cfg)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")

        when:
        def string = '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().withConfig(cfg).readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test big decimal - USE_BIG_DECIMAL_FOR_FLOATS and withExactBigDecimals"() {

        given:
        // withExactBigDecimals(false)
        def cfg = JsonStreamConfig.DEFAULT.withUseBigDecimalForFloats(true)
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), cfg)
        BigDecimal dec = new BigDecimal("888.7794538169553400000")

        when:
        def string = '{"bd1":"888.7794538169553400000","bd2":888.7794538169553400000}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().withConfig(cfg).readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test big integer"() {

        given:
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)
        BigInteger bInt = new BigInteger("9223372036854775807")

        when:
        def string = '{"bi1":"9223372036854775807","bi2":9223372036854775807}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test big integer - USE_BIG_INTEGER_FOR_INTS"() {

        given:
        def cfg = JsonStreamConfig.DEFAULT.withUseBigIntegerForInts(true)
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), cfg)
        BigInteger bInt = new BigInteger("9223372036854775807")

        when:
        def string = '{"bi1":"9223372036854775807","bi2":9223372036854775807}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().withConfig(cfg).readTree(new JsonFactory().createParser(string))
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
        node == expectedNode

    }

    void "test big integer without string - USE_BIG_INTEGER_FOR_INTS"() {

        given:
        def cfg = JsonStreamConfig.DEFAULT.withUseBigIntegerForInts(true)
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), cfg)
        BigInteger bInt = new BigInteger("9223372036854775807")

        when:
        def string = '{"bi1":9223372036854775807,"bi2":9223372036854775807}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().withConfig(cfg).readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test publish JSON node async"() {

        given:
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)


        when:
        def string = '{"name":"Fred","age":10}'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().readTree(new JsonFactory().createParser(string))
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
        node == expectedNode
    }

    void "test publish JSON array async"() {

        given:
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)


        when:
        def string = '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8)
        def expectedNode = JsonNodeTreeCodec.getInstance().readTree(new JsonFactory().createParser(string))
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
        node.isArray()
        node == expectedNode
    }

    void "test incomplete JSON error"() {
        given:
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)


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
        JacksonCoreProcessor processor = new JacksonCoreProcessor(false, new JsonFactory(), JsonStreamConfig.DEFAULT)


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
        JacksonCoreProcessor processor = new JacksonCoreProcessor(true, new JsonFactory(), JsonStreamConfig.DEFAULT)

        when:
        long longValue = Integer.MAX_VALUE + 1L
        BigInteger bigIntegerValue = BigInteger.valueOf(Long.MAX_VALUE) + 1L
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
        nodes[0].equals(JsonNode.createNumberNode(1))
        nodes[1].equals(JsonNode.createNumberNode(longValue))
        nodes[2].size() == 4
        nodes[3].value.toBigDecimal() == bigDecimalValue
        nodes[4].size() == 3
        nodes[5].equals(JsonNode.createNumberNode(11))
        nodes[6].equals(JsonNode.createNumberNode(bigIntegerValue))
    }

}
