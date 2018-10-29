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

package io.micronaut.configuration.kafka.streams.registry;

// tag::imports[]

import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import io.micronaut.context.annotation.Factory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
// end::imports[]

// tag::clazz[]
@Factory
public class WordCountStateStoreStream {

    public static final String INPUT = "statestore-plaintext-input"; // <1>
    public static final String OUTPUT = "statestore-wordcount-output"; // <2>
    public static final String STATE_STORE = "queryable-store-name"; // <3>
// end::clazz[]

    // tag::stateStoreStream[]
    @Singleton
    KStream<String, String> stateStoreStream(ConfiguredStreamBuilder builder) {

        // end::stateStoreStream[]
        // set default serdes
        Properties props = builder.getConfiguration();
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KStream<String, String> source = builder.stream(INPUT);
        // tag::stateStore[]
        KTable<String, Long> counts = source
                .flatMapValues(value -> Arrays.asList(value.toLowerCase(Locale.getDefault()).split(" ")))
                .groupBy((key, value) -> value)
                .count(Materialized.as("queryable-store-name")); // <1>
        // end::stateStore[]
        // need to override value serde to Long type
        counts.toStream().to(OUTPUT, Produced.with(Serdes.String(), Serdes.Long()));
        return source;
    }
}
