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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.DatabindException;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.json.convert.JsonNodeConvertibleValues;
import io.micronaut.json.tree.JsonNode;
import tools.jackson.databind.jsontype.TypeDeserializer;

@Internal
final class ConvertibleValuesDeserializer<V> extends ValueDeserializer<ConvertibleValues<V>> {
    private static final JsonNodeDeserializer JSON_NODE_DESERIALIZER = new JsonNodeDeserializer();
    private final ConversionService conversionService;
    @Nullable
    private final JavaType valueType;
    @Nullable
    private final ValueDeserializer<V> valueDeserializer;
    @Nullable
    private final TypeDeserializer typeDeserializer;

    ConvertibleValuesDeserializer(@NonNull ConversionService conversionService, @Nullable JavaType valueType) {
        this(conversionService, valueType, null, null);
    }

    private ConvertibleValuesDeserializer(@NonNull ConversionService conversionService, @Nullable JavaType valueType, @Nullable ValueDeserializer<V> valueDeserializer, @Nullable TypeDeserializer typeDeserializer) {
        this.conversionService = conversionService;
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.typeDeserializer = typeDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws DatabindException {
        if (valueType == null) {
            // deserialize to JsonNodeConvertibleValues
            return this;
        }
        ValueDeserializer<Object> valueDeserializer = ctxt.findContextualValueDeserializer(valueType, property);

        TypeDeserializer typeDeser = ctxt.findTypeDeserializer(valueType);
        if (typeDeser != null) {
            typeDeser = typeDeser.forProperty(property);
        }

        return new ConvertibleValuesDeserializer<>(conversionService, valueType, valueDeserializer, typeDeser);
    }

    @Override
    public ConvertibleValues<V> deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (valueDeserializer == null) {
            if (!p.hasCurrentToken()) {
                p.nextToken();
            }
            if (p.currentToken() != JsonToken.START_OBJECT) {
                //noinspection unchecked
                return (ConvertibleValues<V>) ctxt.handleUnexpectedToken(handledType(), p);
            }
            JsonNode node = JSON_NODE_DESERIALIZER.deserialize(p, ctxt);
            return new JsonNodeConvertibleValues<>(node, conversionService);
        } else {
            JsonToken t = p.currentToken();
            if (t == JsonToken.START_OBJECT) { // If START_OBJECT, move to next; may also be END_OBJECT
                t = p.nextToken();
            }
            if (t != JsonToken.PROPERTY_NAME && t != JsonToken.END_OBJECT) {
                //noinspection unchecked
                return (ConvertibleValues<V>) ctxt.handleUnexpectedToken(handledType(), p);
            }

            var map = new MutableConvertibleValuesMap<V>();
            map.setConversionService(conversionService);
            for (; p.currentToken() == JsonToken.PROPERTY_NAME; p.nextToken()) {
                // Must point to field name now
                String fieldName = p.currentName();
                p.nextToken();
                Object deserializedValue;
                if (typeDeserializer == null) {
                    deserializedValue = valueDeserializer.deserialize(p, ctxt);
                } else {
                    deserializedValue = valueDeserializer.deserializeWithType(p, ctxt, typeDeserializer);
                }
                map.put(fieldName, (V) deserializedValue);
            }
            return map;
        }
    }
}
