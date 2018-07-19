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

import java.lang.annotation.Annotation;

/**
 * Interface for binders that bind method arguments from a {@link org.apache.kafka.clients.consumer.ConsumerRecord} via a annotation.
 *
 * @param <T> The target type
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotatedConsumerRecordBinder<A extends Annotation, T> extends ConsumerRecordBinder<T> {

    /**
     * @return The annotation type
     */
    Class<A> annotationType();
}
