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

package io.micronaut.configuration.kafka.streams;

import io.micronaut.context.annotation.*;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.KStream;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;


/**
 * A factory that constructs the {@link KafkaStreams} bean.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class KafkaStreamsFactory implements Closeable {

    private final Collection<KafkaStreams> streams = new ConcurrentLinkedDeque<>();
    /**
     * Exposes the {@link ConfiguredStreamBuilder} as a bean.
     *
     * @param configuration The configuration
     * @return The streams builder
     */
    @EachBean(AbtractKafkaStreamsConfiguration.class)
    ConfiguredStreamBuilder streamsBuilder(AbtractKafkaStreamsConfiguration configuration) {
        return new ConfiguredStreamBuilder(configuration.getConfig());
    }

    /**
     * Builds the default {@link KafkaStreams} bean from the configuration and the supplied {@link ConfiguredStreamBuilder}.
     *
     * @param builder The builder
     * @param kStreams The KStream definitions
     * @return The {@link KafkaStreams} bean
     */
    @EachBean(AbtractKafkaStreamsConfiguration.class)
    @Context
    KafkaStreams kafkaStreams(
            ConfiguredStreamBuilder builder,
            // required for initialization. DO NOT DELETE
            KStream... kStreams) {
        KafkaStreams kafkaStreams = new KafkaStreams(
                builder.build(),
                builder.getConfiguration()
        );
        streams.add(kafkaStreams);
        kafkaStreams.start();
        return kafkaStreams;
    }

    @Override
    @PreDestroy
    public void close() {
        for (KafkaStreams stream : streams) {
            try {
                stream.close(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
