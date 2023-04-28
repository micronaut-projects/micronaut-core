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
    private final JavaType valueType;
    private final JsonDeserializer<V> valueDeserializer;

    ConvertibleValuesDeserializer(ConversionService conversionService, JavaType valueType) {
        this(conversionService, valueType, null);
    }

    ConvertibleValuesDeserializer(ConversionService conversionService, JavaType valueType, JsonDeserializer<V> valueDeserializer) {
        this.conversionService = conversionService;
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        JsonDeserializer<Object> valueDeserializer = ctxt.findContextualValueDeserializer(valueType, property);
        return new ConvertibleValuesDeserializer<>(conversionService, valueType, valueDeserializer);
    }

    @Override
    public ConvertibleValues<V> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        if (valueDeserializer == null) {
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
