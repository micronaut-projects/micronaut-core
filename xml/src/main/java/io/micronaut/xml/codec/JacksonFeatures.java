/*
 *
 *  * Copyright 2017-2019 original authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package io.micronaut.xml.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.jackson.codec.JsonStreamMediaTypeCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * Group together jackson features which going to be used to configure Jackson based media codecs.
 *
 * @see JsonMediaTypeCodec
 * @see XmlMediaTypeCodec
 * @see JsonStreamMediaTypeCodec
 * @since 1.2
 */
@Internal
public final class JacksonFeatures {

    private final Map<SerializationFeature, Boolean> serializationFeatures;
    private final Map<DeserializationFeature, Boolean> deserializationFeatures;

    /**
     * Empty jackson features.
     */
    public JacksonFeatures() {
        this.serializationFeatures = new HashMap<>();
        this.deserializationFeatures = new HashMap<>();
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
