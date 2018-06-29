/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.configurations.kafka

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import org.apache.kafka.clients.consumer.Consumer
import spock.lang.Specification

/**
 * @author Iván López
 * @since 1.0
 */
class KafkaConsumerConfigurationSpec extends Specification {

    private PrintStream sysOut
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream()

    void setup() {
        sysOut = System.out
        System.setOut(new PrintStream(outContent))
    }

    void cleanup() {
        System.setOut(sysOut)
    }

    void 'The Consumer bean is created with the default configuration'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect:
        applicationContext.containsBean(Consumer)

        when:
        Consumer kafkaConsumer = applicationContext.getBean(Consumer)

        then:
        kafkaConsumer != null

        and: 'it is configured with the default options'
        String output = outContent.toString()
        output.contains('bootstrap.servers = [localhost:9092]')
        output.contains('key.deserializer = class org.apache.kafka.common.serialization.ByteArrayDeserializer')
        output.contains('value.deserializer = class org.apache.kafka.common.serialization.StringDeserializer')

        cleanup:
        applicationContext.close()
    }

    void 'The default configuration can be changed for Consumer'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            "test",
            [
                'kafka.consumer.key.deserializer'   : 'org.apache.kafka.common.serialization.DoubleDeserializer',
                'kafka.consumer.value.deserializer' : 'org.apache.kafka.common.serialization.IntegerDeserializer',
                'kafka.consumer.bootstrap.servers': 'localhost:9095,localhost:9096,localhost:9097'
            ]
        ))
        applicationContext.start()

        when:
        applicationContext.getBean(Consumer)

        then: 'the default configuration is not applied'
        String output = outContent.toString()
        !output.contains('bootstrap.servers = [localhost:9092]')
        !output.contains('key.deserializer = class org.apache.kafka.common.serialization.ByteArrayDeserializer')
        !output.contains('value.deserializer = class org.apache.kafka.common.serialization.StringDeserializer')

        and: 'the new configuration is used'
        output.contains('bootstrap.servers = [localhost:9095, localhost:9096, localhost:9097]')
        output.contains('key.deserializer = class org.apache.kafka.common.serialization.DoubleDeserializer')
        output.contains('value.deserializer = class org.apache.kafka.common.serialization.IntegerDeserializer')

        cleanup:
        applicationContext.close()
    }

    void 'Additional configuration options can be changed for Consumer'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            "test",
            [
                'kafka.consumer.client.id'      : 'consumer-client-id',
                'kafka.consumer.fetch.max.bytes': '1024'
            ]
        ))
        applicationContext.start()

        when:
        applicationContext.getBean(Consumer)

        then: 'the new configuration is used'
        String output = outContent.toString()
        output.contains('client.id = consumer-client-id')
        output.contains('fetch.max.bytes = 1024')

        cleanup:
        applicationContext.close()
    }

    void 'Only configuration keys under "kafka" namespace are used for Consumer'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            "test",
            [
                'bootstrap.servers'      : 'localhost:1234',
                'kafka.bootstrap.servers': 'localhost:5678'
            ]
        ))
        applicationContext.start()

        when:
        applicationContext.getBean(Consumer)

        then: 'the bootstrap.servers configuration is not used'
        String output = outContent.toString()
        output.contains('bootstrap.servers = [localhost:9092]')
        !output.contains('bootstrap.servers = [localhost:1234]')
        !output.contains('bootstrap.servers = [localhost:5678]')

        cleanup:
        applicationContext.close()
    }
}
