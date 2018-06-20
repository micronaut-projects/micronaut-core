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

package io.micronaut.configuration.kafka.bind;

import io.micronaut.configuration.kafka.KafkaHeaders;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.messaging.MessageHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Obtains the {@link MessageHeaders} object for Kafka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class KafkaHeadersBinder implements TypedConsumerRecordBinder<MessageHeaders> {

    public static final Argument<MessageHeaders> TYPE = Argument.of(MessageHeaders.class);

    @Override
    public Argument<MessageHeaders> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<MessageHeaders> bind(ArgumentConversionContext<MessageHeaders> context, ConsumerRecord<?, ?> source) {

        KafkaHeaders kafkaHeaders = new KafkaHeaders(source.headers());
        return () -> Optional.of(kafkaHeaders);
    }
}
