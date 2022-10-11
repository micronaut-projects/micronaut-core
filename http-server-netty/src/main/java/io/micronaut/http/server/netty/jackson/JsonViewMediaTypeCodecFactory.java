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
package io.micronaut.http.server.netty.jackson;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory to produce {@link io.micronaut.http.codec.MediaTypeCodec} for JSON and Jackson using a specified
 * JsonView class.
 *
 * @since 1.1
 * @author mmindenhall
 * @author graemerocher
 */
@Requires(beans = JsonConfiguration.class)
@Requires(property = JsonViewServerFilter.PROPERTY_JSON_VIEW_ENABLED)
@Singleton
@Primary
class JsonViewMediaTypeCodecFactory implements JsonViewCodecResolver {

    private final JsonMediaTypeCodec jsonCodec;
    private final Map<Class<?>, JsonMediaTypeCodec> jsonViewCodecs = new ConcurrentHashMap<>(5);

    JsonViewMediaTypeCodecFactory(JsonMediaTypeCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    /**
     * Creates a {@link JsonMediaTypeCodec} for the view class (specified as the JsonView annotation value).
     * @param viewClass The view class
     * @return The codec
     */
    @Override
    @NonNull
    public JsonMediaTypeCodec resolveJsonViewCodec(@NonNull Class<?> viewClass) {
        ArgumentUtils.requireNonNull("viewClass", viewClass);
        JsonMediaTypeCodec codec = jsonViewCodecs.get(viewClass);
        if (codec == null) {
            codec = (JsonMediaTypeCodec) jsonCodec.cloneWithViewClass(viewClass);
            jsonViewCodecs.put(viewClass, codec);
        }
        return codec;
    }
}
