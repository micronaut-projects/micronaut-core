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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;

/**
 * Serializer for {@link ConvertibleValues}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ConvertibleValuesSerializer extends JsonSerializer<ConvertibleValues<?>> {

    @Override
    public boolean isEmpty(SerializerProvider provider, ConvertibleValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(ConvertibleValues<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        for (Map.Entry<String, ?> entry : value) {
            String fieldName = entry.getKey();
            Object v = entry.getValue();
            if (v != null) {
                gen.writeFieldName(fieldName);
                gen.writeObject(v);
            }
        }
        gen.writeEndObject();
    }
}
