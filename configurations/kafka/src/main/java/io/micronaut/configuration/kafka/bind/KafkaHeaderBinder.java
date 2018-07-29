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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.messaging.annotation.Header;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Binds Kafka headers to arguments.
 *
 * @param <T> The target type
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class KafkaHeaderBinder<T> implements AnnotatedConsumerRecordBinder<Header, T> {

    @Override
    public Class<Header> annotationType() {
        return Header.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, ConsumerRecord<?, ?> source) {
        Headers headers = source.headers();
        AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();

        String name = annotationMetadata.getValue(Header.class, "name", String.class)
                                        .orElseGet(() -> annotationMetadata.getValue(Header.class, String.class)
                                                                           .orElse(context.getArgument().getName()));
        Iterable<org.apache.kafka.common.header.Header> value = headers.headers(name);

        if (value.iterator().hasNext()) {
            Optional<T> converted = ConversionService.SHARED.convert(value, context);
            return () -> converted;
        } else {
            //noinspection unchecked
            return BindingResult.EMPTY;
        }
    }
}
