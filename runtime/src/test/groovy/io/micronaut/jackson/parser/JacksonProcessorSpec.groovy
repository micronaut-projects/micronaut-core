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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.io.JsonEOFException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
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

    void "test publish JSON node async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor()
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

    void "test Jackson polymorphic deserialization"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor()
        Bicycle bicycle = new MountainBike(3, 30, 10)
        Garage instance = new Garage(bicycle: bicycle)

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
        string == '{"bicycle":{"gear":3,"speed":30,"seatHeight":10}}'

        when:
        Garage garage = objectMapper.treeToValue(node, Garage)

        then:
        !(garage.bicycle instanceof MountainBike)
    }

    void "test Jackson polymorphic deserialization, per class"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor()
        Dog dog = new Dog(name: "Daisy", barkVolume: 1111.1D)
        Zoo instance = new Zoo(animal: dog)

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
        string == '{"animal":{"@class":"io.micronaut.jackson.parser.Dog","name":"Daisy","barkVolume":1111.1}}'

        when:
        Zoo zoo = objectMapper.treeToValue(node, Zoo)

        then:
        zoo.animal instanceof Dog

        when:
        Dog animal = (Dog) zoo.animal

        then:
        animal != null
        animal.name == "Daisy"
        animal.barkVolume == 1111.1D
    }

    void "test Jackson polymorphic deserialization, global default typing"() {
        given:
        ApplicationContext applicationContext1 = new DefaultApplicationContext("test")
        applicationContext1.environment.addPropertySource(PropertySource.of(
                'test',  ['jackson.defaultTyping':'NON_FINAL']
        ))
        applicationContext1 = applicationContext1.start()
        ObjectMapper objectMapper = applicationContext1.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor()
        Bicycle bicycle = new MountainBike(3, 30, 10)
        Garage instance = new Garage(bicycle: bicycle)

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
        string == '["io.micronaut.jackson.parser.Garage",{"bicycle":["io.micronaut.jackson.parser.MountainBike",{"gear":3,"speed":30,"seatHeight":10}]}]'

        when:
        Garage garage = objectMapper.treeToValue(node, Garage)

        then:
        garage.bicycle instanceof MountainBike

        when:
        MountainBike mountainBike = (MountainBike) garage.bicycle

        then:
        mountainBike != null
        mountainBike.gear == 3
        mountainBike.speed == 30
        mountainBike.seatHeight == 10
    }

    void "test publish JSON array async"() {

        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)
        JacksonProcessor processor = new JacksonProcessor()
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
        JacksonProcessor processor = new JacksonProcessor()


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
        JacksonProcessor processor = new JacksonProcessor()


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

}

class Foo {
    String name
    Integer age
}

@ToString
class Garage {
    String name
    Bicycle bicycle
}

@ToString
class Bicycle {
    int gear
    int speed

    Bicycle() {}

    Bicycle(int gear, int speed) {
        this.gear = gear
        this.speed = speed
    }

    void applyBrake(int decrement) {
        speed -= decrement
    }

    void speedUp(int increment) {
        speed += increment
    }
}

@ToString
class MountainBike extends Bicycle {
    int seatHeight

    MountainBike() {}
    
    MountainBike(int gear,int speed, int seatHeight) {
        super(gear, speed)
        this.seatHeight = seatHeight
    }
}

@ToString
class Zoo {
    Animal animal
}

@ToString
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="@class")
class Animal {
    String name
}

@ToString
class Dog extends Animal {
    double barkVolume

}
