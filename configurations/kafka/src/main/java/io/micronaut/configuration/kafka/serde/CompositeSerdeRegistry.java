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

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.serialize.exceptions.SerializationException;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link SerdeRegistry} that combines multiple registries into a single registry.
 *
 * @author Graeme Rocher
 */
@Singleton
@Primary
public class CompositeSerdeRegistry implements SerdeRegistry {

    private final List<SerdeRegistry> registries;
    private final Map<Class, Serde> serdeMap = new ConcurrentHashMap<>();

    /**
     * The default constructor.
     *
     * @param registries The other registries
     */
    public CompositeSerdeRegistry(SerdeRegistry... registries) {
        this.registries = new ArrayList<>(
                registries != null ? Arrays.asList(registries) : Collections.emptyList()
        );

        OrderUtil.sort(registries);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T> Serde<T> getSerde(Class<T> type) {
        Serde serde = serdeMap.get(type);

        if (serde != null) {
           return serde;
        } else {
            type = ReflectionUtils.getWrapperType(type);
            try {
                return Serdes.serdeFrom(type);
            } catch (IllegalArgumentException e) {
                for (SerdeRegistry registry : registries) {
                    serde = registry.getSerde(type);
                    if (serde != null) {
                        serdeMap.put(type, serde);
                        return serde;
                    }
                }
            }
            throw new SerializationException("No available serde for type: " + type);
        }
    }
}
