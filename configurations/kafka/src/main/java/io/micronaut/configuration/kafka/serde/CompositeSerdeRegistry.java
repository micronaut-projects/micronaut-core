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
import org.apache.kafka.common.serialization.Serde;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.*;

/**
 * The default {@link SerdeRegistry} that combines multiple registries into a single registry.
 *
 * @author Graeme Rocher
 */
@Singleton
@Primary
public class CompositeSerdeRegistry implements SerdeRegistry {

    private final List<SerdeRegistry> registries;

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

    @Override
    @Nonnull
    public <T> Optional<Serde<T>> getSerde(Class<T> type) {
        for (SerdeRegistry registry : registries) {
            Optional<Serde<T>> serde = registry.getSerde(type);
            if (serde.isPresent()) {
                return serde;
            }
        }
        return Optional.empty();
    }
}
