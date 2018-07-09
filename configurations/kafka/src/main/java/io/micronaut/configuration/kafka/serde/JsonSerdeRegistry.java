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

import io.micronaut.context.BeanContext;
import org.apache.kafka.common.serialization.Serde;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SerdeRegistry} that computes {@link Serde} instances that use Jackson to JSON serialization.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonSerdeRegistry implements SerdeRegistry {

    private final BeanContext beanContext;
    private final Map<Class, JsonSerde> serdes = new ConcurrentHashMap<>();

    /**
     * Constructs a new instance.
     *
     * @param beanContext The bean context
     */
    protected JsonSerdeRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Serde<T> getSerde(Class<T> type) {
        JsonSerde jsonSerde = serdes.get(type);
        if (jsonSerde != null) {
            return jsonSerde;
        } else {
            jsonSerde = beanContext.createBean(JsonSerde.class, type);
            serdes.put(type, jsonSerde);
            return jsonSerde;
        }
    }
}
