/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.json.convert.JsonNodeConvertibleValues;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;

@Internal
final class ConvertibleValuesDeserializer<V> extends JsonDeserializer<ConvertibleValues<V>> implements ContextualDeserializer {
    private static final JsonNodeDeserializer JSON_NODE_DESERIALIZER = new JsonNodeDeserializer();
    private final ConversionService conversionService;
    @Nullable
    private final JavaType valueType;
    @Nullable
    private final JsonDeserializer<V> valueDeserializer;

    ConvertibleValuesDeserializer(@NonNull ConversionService conversionService, @Nullable JavaType valueType) {
        this(conversionService, valueType, null);
    }

    private ConvertibleValuesDeserializer(@NonNull ConversionService conversionService, @Nullable JavaType valueType, @Nullable JsonDeserializer<V> valueDeserializer) {
        this.conversionService = conversionService;
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        if (valueType == null) {
            // deserialize to JsonNodeConvertibleValues
            return this;
        }
        JsonDeserializer<Object> valueDeserializer = ctxt.findContextualValueDeserializer(valueType, property);
        return new ConvertibleValuesDeserializer<>(conversionService, valueType, valueDeserializer);
    }

    @Override
    public ConvertibleValues<V> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        if (valueDeserializer == null) {
            if (!p.hasCurrentToken()) {
                p.nextToken();
            }
            if (p.getCurrentToken() != JsonToken.START_OBJECT) {
                //noinspection unchecked
                return (ConvertibleValues<V>) ctxt.handleUnexpectedToken(handledType(), p);
            }
            JsonNode node = JSON_NODE_DESERIALIZER.deserialize(p, ctxt);
            return new JsonNodeConvertibleValues<>(node, conversionService);
        } else {
            JsonToken t = p.getCurrentToken();
            if (t == JsonToken.START_OBJECT) { // If START_OBJECT, move to next; may also be END_OBJECT
                t = p.nextToken();
            }
            if (t != JsonToken.FIELD_NAME && t != JsonToken.END_OBJECT) {
                //noinspection unchecked
                return (ConvertibleValues<V>) ctxt.handleUnexpectedToken(handledType(), p);
            }

            MutableConvertibleValuesMap<V> map = new MutableConvertibleValuesMap<>();
            map.setConversionService(conversionService);
            for (; p.getCurrentToken() == JsonToken.FIELD_NAME; p.nextToken()) {
                // Must point to field name now
                String fieldName = p.getCurrentName();
                p.nextToken();
                map.put(fieldName, valueDeserializer.deserialize(p, ctxt));
            }
            return map;
        }
    }
}
