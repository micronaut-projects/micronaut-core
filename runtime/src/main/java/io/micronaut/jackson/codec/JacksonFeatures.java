/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.codec;

import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.core.annotation.Internal;

/**
 * Stores features to later configure an {@link com.fasterxml.jackson.databind.ObjectMapper}.
 * Features are supplied through the {@link io.micronaut.jackson.annotation.JacksonFeatures} annotation.
 *
 * @author svishnyakov
 * @since 1.3.0
 */
@Internal
public final class JacksonFeatures {

    private final Map<SerializationFeature, Boolean> serializationFeatures;
    private final Map<DeserializationFeature, Boolean> deserializationFeatures;

    /**
     * Empty jackson features.
     */
    public JacksonFeatures() {
        this.serializationFeatures = new EnumMap<>(SerializationFeature.class);
        this.deserializationFeatures = new EnumMap<>(DeserializationFeature.class);
    }

    /**
     * Add a serialization feature.
     *
     * @param serializationFeature serialization feature to enable/disable
     * @param isEnabled            whether you want to turn feature on/off
     * @return This object.
     */
    public JacksonFeatures addFeature(SerializationFeature serializationFeature, boolean isEnabled) {
        serializationFeatures.put(serializationFeature, isEnabled);
        return this;
    }

    /**
     * Add a deserialization feature.
     *
     * @param deserializationFeature deserialization feature to enable/disable
     * @param isEnabled              whether you want to turn feature on/off
     * @return This object.
     */
    public JacksonFeatures addFeature(DeserializationFeature deserializationFeature, boolean isEnabled) {
        deserializationFeatures.put(deserializationFeature, isEnabled);
        return this;
    }

    /**
     * Serialization features.
     *
     * @return Serialization features or empty map if none available.
     */
    public Map<SerializationFeature, Boolean> getSerializationFeatures() {
        return this.serializationFeatures;
    }

    /**
     * Deserialization features.
     *
     * @return Deserialization features or empty map if none available.
     */
    public Map<DeserializationFeature, Boolean> getDeserializationFeatures() {
        return this.deserializationFeatures;
    }
}
