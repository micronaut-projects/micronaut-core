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
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Optional;

/**
 * The default binder that binds the Kafka value for a ConsumerRecord.
 *
 * @param <T> The target generic type
 * @author Graeme Rocher
 * @since 1.0
 */
public class KafkaValueBinder<T> implements ConsumerRecordBinder<T> {
    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, ConsumerRecord<?, ?> source) {
        if (context.getArgument().getType() == ConsumerRecord.class) {
            Optional<? extends ConsumerRecord<?, ?>> opt = Optional.of(source);
            //noinspection unchecked
            return () -> (Optional<T>) opt;
        } else {
            Object value = source.value();
            Optional<T> converted = ConversionService.SHARED.convert(value, context);
            return () -> converted;
        }
    }
}
