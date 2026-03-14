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
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.jackson.JacksonConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * A Jackson Serializer for {@link OptionalValues}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class OptionalValuesSerializer extends ValueSerializer<OptionalValues<?>> {

    private final boolean alwaysSerializeErrorsAsList;

    public OptionalValuesSerializer() {
        this.alwaysSerializeErrorsAsList = false;
    }

    @Inject
    public OptionalValuesSerializer(JacksonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public boolean isEmpty(SerializationContext provider, OptionalValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(OptionalValues<?> value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();

        for (CharSequence key : value) {
            Optional<?> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                gen.writeName(fieldName);
                Object v = opt.get();
                if (value instanceof OptionalMultiValues) {
                    List<?> list = (List<?>) v;

                    if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                        gen.writePOJO(list.get(0));
                    } else {
                        gen.writePOJO(list);
                    }
                } else {
                    gen.writePOJO(v);
                }
            }
        }
        gen.writeEndObject();
    }
}
