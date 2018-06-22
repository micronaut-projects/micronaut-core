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

package io.micronaut.configuration.kafka.serde;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Optional;
import java.util.concurrent.Future;


/**
 * A registry of Kafka {@link org.apache.kafka.common.serialization.Serde} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface SerdeRegistry extends Ordered {

    /**
     * Obtain a {@link Serde} for the given type.
     *
     * @param <T>  The generic type
     * @param type The type
     * @return The {@link Serde}
     */
    <T> Serde<T> getSerde(Class<T> type);

    /**
     * Obtain a {@link Serializer} for the given type.
     *
     * @param <T>  The generic type
     * @param type The type
     * @return The {@link Serde}
     */
    @SuppressWarnings("unchecked")
    default <T> Serializer<T> getSerializer(Class<T> type) {
        return getSerde(type).serializer();
    }

    /**
     * Obtain a {@link Deserializer} for the given type.
     *
     * @param <T>  The generic type
     * @param type The type
     * @return The {@link Serde}
     */
    @SuppressWarnings("unchecked")
    default <T> Deserializer<T> getDeserializer(Class<T> type) {
        return getSerde(type).deserializer();
    }

    /**
     * Picks the most appropriate {@link Deserializer} for the given argument.
     *
     * @param argument The argument
     * @param <T> The generic type
     * @return The {@link Deserializer}
     */
    @SuppressWarnings("unchecked")
    default <T> Deserializer<T> pickDeserializer(Argument<T> argument) {
        Class<T> type = argument.getType();

        if (Publishers.isConvertibleToPublisher(type) || Future.class.isAssignableFrom(type)) {
            Optional<Argument<?>> typeArg = argument.getFirstTypeVariable();

            if (typeArg.isPresent()) {
                type = (Class<T>) typeArg.get().getType();
            } else {
                return (Deserializer<T>) new ByteArrayDeserializer();
            }
        }

        return getDeserializer(type);
    }

    /**
     * Picks the most appropriate {@link Deserializer} for the given argument.
     *
     * @param argument The argument
     * @param <T> The generic type
     * @return The {@link Deserializer}
     */
    @SuppressWarnings("unchecked")
    default <T> Serializer<T> pickSerializer(Argument<T> argument) {
        Class<T> type = argument.getType();

        if (Publishers.isConvertibleToPublisher(type) || Future.class.isAssignableFrom(type)) {
            Optional<Argument<?>> typeArg = argument.getFirstTypeVariable();

            if (typeArg.isPresent()) {
                type = (Class<T>) typeArg.get().getType();
            } else {
                return (Serializer<T>) new ByteArrayDeserializer();
            }
        }

        return getSerializer(type);
    }
}
