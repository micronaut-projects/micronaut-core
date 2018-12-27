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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.annotation.Body;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * The default binder that binds the body of a ConsumerRecord.
 *
 * @deprecated Use {@link KafkaMessagingBodyBinder} instead
 * @param <T> The target generic type
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Deprecated
public class KafkaBodyBinder<T> implements AnnotatedConsumerRecordBinder<Body, T> {

    protected static final Logger LOG = LoggerFactory.getLogger(KafkaBodyBinder.class);

    @Override
    public Class<Body> annotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, ConsumerRecord<?, ?> source) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Argument [" + context.getArgument().getTypeString(true) + "]. Using the io.micronaut.http.annotation.Body annotation for binding Kafka message bodies is deprecated and will be removed in a future major release. Use io.micronaut.messaging.annotation.Body instead.");
        }
        Object value = source.value();
        Optional<T> converted = ConversionService.SHARED.convert(value, context);
        return () -> converted;
    }
}
