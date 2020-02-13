/*
 * Copyright 2017-2019 original authors
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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.core.value.OptionalValues;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * A Jackson Serializer for {@link OptionalValues}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class OptionalValuesSerializer extends JsonSerializer<OptionalValues<?>> {

    @Override
    public boolean isEmpty(SerializerProvider provider, OptionalValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(OptionalValues<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        for (CharSequence key : value) {
            Optional<?> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                gen.writeFieldName(fieldName);
                Object v = opt.get();
                if (value instanceof OptionalMultiValues) {
                    List list = (List) v;
                    if (serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED) && list.size() == 1) {
                        gen.writeObject(list.get(0));
                    } else {
                        gen.writeObject(list);
                    }
                } else {
                    gen.writeObject(v);
                }
            }
        }
        gen.writeEndObject();
    }
}
