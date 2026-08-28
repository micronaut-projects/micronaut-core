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
package io.micronaut.jackson.serialize;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Serializer for {@link ConvertibleMultiValues}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ConvertibleMultiValuesSerializer extends ValueSerializer<ConvertibleMultiValues<?>> {

    @Override
    public boolean isEmpty(SerializationContext provider, ConvertibleMultiValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(ConvertibleMultiValues<?> value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();

        for (Map.Entry<String, ? extends List<?>> entry : value) {
            String fieldName = entry.getKey();
            List<?> v = entry.getValue();
            int len = v.size();
            if (len > 0) {
                if (len == 1) {
                    serializers.defaultSerializeProperty(fieldName, v.get(0), gen);
                } else {
                    gen.writeName(fieldName);
                    gen.writeStartArray();

                    for (Object o : v) {
                        serializers.writeValue(gen, o);
                    }
                    gen.writeEndArray();
                }
            }
        }
        gen.writeEndObject();
    }
}
