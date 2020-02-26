/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.core.convert.value.ConvertibleMultiValues;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Serializer for {@link ConvertibleMultiValues}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ConvertibleMultiValuesSerializer extends JsonSerializer<ConvertibleMultiValues<?>> {

    @Override
    public boolean isEmpty(SerializerProvider provider, ConvertibleMultiValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(ConvertibleMultiValues<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        for (Map.Entry<String, ? extends List<?>> entry : value) {
            String fieldName = entry.getKey();
            List<?> v = entry.getValue();
            int len = v.size();
            if (len > 0) {
                gen.writeFieldName(fieldName);
                if (len == 1) {
                    gen.writeObject(v.get(0));
                } else {
                    gen.writeStartArray();

                    for (Object o : v) {
                        gen.writeObject(o);
                    }
                    gen.writeEndArray();
                }
            }
        }
        gen.writeEndObject();
    }
}
