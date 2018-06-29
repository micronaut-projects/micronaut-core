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
import org.apache.kafka.clients.producer.Producer
import spock.lang.Specification

/**
 * @author Iván López
 * @since 1.0
 */
class KafkaProducerConfigurationSpec extends Specification {

    private PrintStream sysOut
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream()

    void setup() {
        sysOut = System.out
        System.setOut(new PrintStream(outContent))
    }

    void cleanup() {
        System.setOut(sysOut)
    }

    void 'The Producer bean is created with the default configuration'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect:
        applicationContext.containsBean(Producer)

        when:
        Producer kafkaProducer = applicationContext.getBean(Producer)

        then:
        kafkaProducer != null

        and: 'it is configured with the default options'
        String output = outContent.toString()
        output.contains('bootstrap.servers = [localhost:9092]')
        output.contains('key.serializer = class org.apache.kafka.common.serialization.ByteArraySerializer')
        output.contains('value.serializer = class org.apache.kafka.common.serialization.StringSerializer')

        cleanup:
        applicationContext.close()
    }

    void 'The default configuration can be changed for Producer'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            "test",
            [
                'kafka.producer.key.serializer'   : 'org.apache.kafka.common.serialization.DoubleSerializer',
                'kafka.producer.value.serializer' : 'org.apache.kafka.common.serialization.IntegerSerializer',
                'kafka.producer.bootstrap.servers': 'localhost:9095,localhost:9096,localhost:9097'
            ]
        ))
        applicationContext.start()

        when:
        applicationContext.getBean(Producer)

        then: 'the default configuration is not applied'
        String output = outContent.toString()
        !output.contains('bootstrap.servers = [localhost:9092]')
        !output.contains('key.serializer = class org.apache.kafka.common.serialization.ByteArraySerializer')
        !output.contains('value.serializer = class org.apache.kafka.common.serialization.StringSerializer')

        and: 'the new configuration is used'
        output.contains('bootstrap.servers = [localhost:9095, localhost:9096, localhost:9097]')
        output.contains('key.serializer = class org.apache.kafka.common.serialization.DoubleSerializer')
        output.contains('value.serializer = class org.apache.kafka.common.serialization.IntegerSerializer')

        cleanup:
        applicationContext.close()
    }

    void 'Additional configuration options can be changed for Producer'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            "test",
            [
                'kafka.producer.acks'      : 'all',
                'kafka.producer.batch.size': '1024'
            ]
        ))
        applicationContext.start()

        when:
        applicationContext.getBean(Producer)

        then: 'the new configuration is used'
        String output = outContent.toString()
        output.contains('acks = all')
        output.contains('batch.size = 1024')

        cleanup:
        applicationContext.close()
    }

    void 'Only configuration keys under "kafka" namespace are used for Producer'() {
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
        applicationContext.getBean(Producer)

        then: 'the bootstrap.servers configuration is not used'
        String output = outContent.toString()
        output.contains('bootstrap.servers = [localhost:9092]')
        !output.contains('bootstrap.servers = [localhost:1234]')
        !output.contains('bootstrap.servers = [localhost:5678]')

        cleanup:
        applicationContext.close()
    }
}
