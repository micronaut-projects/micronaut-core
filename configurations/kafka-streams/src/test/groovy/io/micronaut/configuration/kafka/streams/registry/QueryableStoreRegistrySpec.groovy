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

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.QueryableStoreType
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import spock.lang.Specification

class QueryableStoreRegistrySpec extends Specification {

    def "test getQueryableStoreType"() {
        given:
        ReadOnlyKeyValueStore readOnlyKeyValueStore = Mock(ReadOnlyKeyValueStore)
        KafkaStreams kafkaStreams = Mock(KafkaStreams)
        KafkaStreamsRegistry kafkaStreamsRegistry = new KafkaStreamsRegistry([kafkaStreams] as Set)
        QueryableStoreRegistry queryableStoreRegistry = new QueryableStoreRegistry(kafkaStreamsRegistry)

        when:
        def queryableStoreType = queryableStoreRegistry.getQueryableStoreType("foo", QueryableStoreTypes.keyValueStore())

        then:
        1 * kafkaStreams.store("foo", _ as QueryableStoreType) >> readOnlyKeyValueStore

        when:
        def get = queryableStoreType.get("bar")

        then:
        get == 10
        1 * readOnlyKeyValueStore.get("bar") >> 10

    }
}
