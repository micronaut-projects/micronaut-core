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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.json.JsonFeatures;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores features to later configure an {@link com.fasterxml.jackson.databind.ObjectMapper}.
 * Features are supplied through the {@link io.micronaut.jackson.annotation.JacksonFeatures} annotation.
 *
 * @author svishnyakov
 * @since 1.3.0
 */
@Internal
public final class JacksonFeatures implements JsonFeatures {

    private final Map<SerializationFeature, Boolean> serializationFeatures;
    private final Map<DeserializationFeature, Boolean> deserializationFeatures;
    private final List<Class<? extends Module>> additionalModules;

    /**
     * Empty jackson features.
     */
    public JacksonFeatures() {
        this.serializationFeatures = new EnumMap<>(SerializationFeature.class);
        this.deserializationFeatures = new EnumMap<>(DeserializationFeature.class);
        this.additionalModules = new ArrayList<>();
    }

    public static JacksonFeatures fromAnnotation(AnnotationValue<io.micronaut.jackson.annotation.JacksonFeatures> jacksonFeaturesAnn) {
        JacksonFeatures jacksonFeatures = new JacksonFeatures();


        SerializationFeature[] enabledSerializationFeatures = jacksonFeaturesAnn.enumValues("enabledSerializationFeatures", SerializationFeature.class);
        if (ArrayUtils.isNotEmpty(enabledSerializationFeatures)) {
            for (SerializationFeature serializationFeature : enabledSerializationFeatures) {
                jacksonFeatures.addFeature(serializationFeature, true);
            }
        }

        DeserializationFeature[] enabledDeserializationFeatures = jacksonFeaturesAnn.enumValues("enabledDeserializationFeatures", DeserializationFeature.class);

        if (ArrayUtils.isNotEmpty(enabledDeserializationFeatures)) {
            for (DeserializationFeature deserializationFeature : enabledDeserializationFeatures) {
                jacksonFeatures.addFeature(deserializationFeature, true);
            }
        }

        SerializationFeature[] disabledSerializationFeatures = jacksonFeaturesAnn.enumValues("disabledSerializationFeatures", SerializationFeature.class);
        if (ArrayUtils.isNotEmpty(disabledSerializationFeatures)) {
            for (SerializationFeature serializationFeature : disabledSerializationFeatures) {
                jacksonFeatures.addFeature(serializationFeature, false);
            }
        }

        DeserializationFeature[] disabledDeserializationFeatures = jacksonFeaturesAnn.enumValues("disabledDeserializationFeatures", DeserializationFeature.class);

        if (ArrayUtils.isNotEmpty(disabledDeserializationFeatures)) {
            for (DeserializationFeature feature : disabledDeserializationFeatures) {
                jacksonFeatures.addFeature(feature, false);
            }
        }

        Class<?>[] additionalModules = jacksonFeaturesAnn.classValues("additionalModules");
        if (ArrayUtils.isNotEmpty(additionalModules)) {
            for (Class<?> additionalModule : additionalModules) {
                //noinspection unchecked
                jacksonFeatures.addModule((Class<? extends Module>) additionalModule);
            }
        }

        return jacksonFeatures;
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
     * Add a jackson module feature.
     *
     * @param moduleClass The module to load
     * @return This object.
     * @since 3.2
     */
    @NonNull
    public JacksonFeatures addModule(@NonNull Class<? extends Module> moduleClass) {
        Objects.requireNonNull(moduleClass, "moduleClass");
        additionalModules.add(moduleClass);
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

    /**
     * Additional modules to load.
     *
     * @return List of additional modules to load.
     * @since 3.2
     */
    @NonNull
    public List<Class<? extends Module>> getAdditionalModules() {
        return additionalModules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JacksonFeatures that = (JacksonFeatures) o;
        return Objects.equals(serializationFeatures, that.serializationFeatures) && Objects.equals(deserializationFeatures, that.deserializationFeatures);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(serializationFeatures, deserializationFeatures);
    }
}
