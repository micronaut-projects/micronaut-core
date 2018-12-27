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

package io.micronaut.configuration.kafka.streams.registry

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import org.apache.kafka.streams.state.QueryableStoreTypes
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaStreamQueryableStoreSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, [WordCountStateStoreStream.INPUT, WordCountStateStoreStream.OUTPUT],
                    'kafka.streams.my-stream.num.stream.threads', 10,
                    'kafka.streams.state-stream.num.stream.threads', 10
            )
    )

    void "test the number of streams registered"() {
        when:
        def kafkaStreamsRegistry = context.getBean(KafkaStreamsRegistry)
        def queryableStoreRegistry = context.getBean(QueryableStoreRegistry)

        then: 'Just make sure there are some streams in-case more stream tests are added messing up the count'
        kafkaStreamsRegistry.kafkaStreams.size() >= 1

        and:
        queryableStoreRegistry
    }

    void "test kafka stream application state store query"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        WordCountStateStoreClient wordCountStateStoreClient = context.getBean(WordCountStateStoreClient)
        QueryableStoreRegistry queryableStoreRegistry = context.getBean(QueryableStoreRegistry)

        when:
        wordCountStateStoreClient.publishSentence("The quick brown fox jumps over the lazy dog. THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG'S BACK")

        then:
        conditions.eventually {
            queryableStoreRegistry.getQueryableStoreType(WordCountStateStoreStream.STATE_STORE, QueryableStoreTypes.<String, Long> keyValueStore()).get("fox") > 0
            queryableStoreRegistry.getQueryableStoreType(WordCountStateStoreStream.STATE_STORE, QueryableStoreTypes.keyValueStore()).get("fox") > 0
        }
    }

    void "test kafka stream application state store query using example bean"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        WordCountStateStoreClient wordCountStateStoreClient = context.getBean(WordCountStateStoreClient)
        QueryableStoreRegistryExample queryableStoreRegistryExample = context.getBean(QueryableStoreRegistryExample)

        when:
        wordCountStateStoreClient.publishSentence("The quick brown fox jumps over the lazy dog. THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG'S BACK")

        then:
        conditions.eventually {
            queryableStoreRegistryExample.getStore(WordCountStateStoreStream.STATE_STORE).get("fox") > 0
            queryableStoreRegistryExample.getValue(WordCountStateStoreStream.STATE_STORE, "fox") > 0
        }
    }
}
