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
import io.micronaut.core.type.Argument;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The default binder that binds the Kafka value for a ConsumerRecord.
 *
 * @param <T> The target generic type
 * @author Graeme Rocher
 * @since 1.0
 */
public class KafkaDefaultBinder<T> implements ConsumerRecordBinder<T> {

    private final Map<Argument, Function<ConsumerRecord<?, ?>, Object>> defaultResolver;

    /**
     * Default constructor.
     */
    public KafkaDefaultBinder() {
        this.defaultResolver = new HashMap<>();
        this.defaultResolver.put(
                Argument.of(String.class, "topic"),
                ConsumerRecord::topic
        );
        this.defaultResolver.put(
                Argument.of(Long.class, "offset"),
                ConsumerRecord::offset
        );
        this.defaultResolver.put(
                Argument.of(Integer.class, "partition"),
                ConsumerRecord::partition
        );
        this.defaultResolver.put(
                Argument.of(Long.class, "timestamp"),
                ConsumerRecord::timestamp
        );

        this.defaultResolver.put(
                Argument.of(long.class, "offset"),
                ConsumerRecord::offset
        );
        this.defaultResolver.put(
                Argument.of(int.class, "partition"),
                ConsumerRecord::partition
        );
        this.defaultResolver.put(
                Argument.of(long.class, "timestamp"),
                ConsumerRecord::timestamp
        );
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, ConsumerRecord<?, ?> source) {
        Argument<T> argument = context.getArgument();
        Function<ConsumerRecord<?, ?>, Object> f = defaultResolver.get(argument);
        if (f != null) {
            Optional<Object> opt = Optional.of(f.apply(source));
            return () -> (Optional<T>) opt;
        } else if (argument.getType() == ConsumerRecord.class) {
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
