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

package io.micronaut.configuration.kafka.exceptions;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * The default ExceptionHandler used when a {@link org.apache.kafka.clients.consumer.KafkaConsumer}
 * fails to process a {@link org.apache.kafka.clients.consumer.ConsumerRecord}. By default just logs the error.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultKafkaListenerExceptionHandler implements KafkaListenerExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaListenerExceptionHandler.class);

    @Override
    public void handle(KafkaListenerException exception) {
        if (LOG.isErrorEnabled()) {
            Optional<ConsumerRecord<?, ?>> consumerRecord = exception.getConsumerRecord();
            if (consumerRecord.isPresent()) {
                LOG.error("Error processing record [" + consumerRecord + "] for Kafka consumer [" + exception.getKafkaListener() + "] produced error: " + exception.getCause().getMessage(), exception.getCause());

            } else {
                LOG.error("Kafka consumer [" + exception.getKafkaListener() + "] produced error: " + exception.getCause().getMessage(), exception.getCause());
            }
        }
    }
}
