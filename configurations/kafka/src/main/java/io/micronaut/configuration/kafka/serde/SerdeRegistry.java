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

import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import org.apache.kafka.common.serialization.*;

import java.util.Collections;
import java.util.Map;


/**
 * A registry of Kafka {@link org.apache.kafka.common.serialization.Serde} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface SerdeRegistry extends Ordered {

    /**
     * Default deserializers.
     */
    @SuppressWarnings({"unused", "unchecked"})
    Map<Class, Deserializer> DEFAULT_DESERIALIZERS = Collections.unmodifiableMap(
        CollectionUtils.mapOf(
            String.class, new StringDeserializer(),
            Integer.class, new IntegerDeserializer(),
            Float.class, new FloatDeserializer(),
            Short.class, new ShortDeserializer(),
            Long.class, new LongDeserializer(),
            Double.class, new DoubleDeserializer(),
            byte[].class, new ByteArrayDeserializer()
        )
    );

    /**
     * Obtain a {@link Serde} for the given type.
     *
     * @param type The type
     * @param <T> The generic type
     * @return The {@link Serde}
     */
    <T> Serde<T> getSerde(Class<T> type);
}
