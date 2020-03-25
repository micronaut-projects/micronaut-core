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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import javax.inject.Provider;
import java.util.Optional;

/**
 * <p>A {@link TypeConverter} that can convert objects to JSON with Jackson.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link JacksonConverterRegistrar}
 */
@Deprecated
public class ObjectToJsonNodeConverter implements TypeConverter<Object, JsonNode> {

    private final Provider<ObjectMapper> objectMapper;

    /**
     * @param objectMapper The object mapper provider to read/write JSON
     */
    public ObjectToJsonNodeConverter(Provider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<JsonNode> convert(Object object, Class<JsonNode> targetType, ConversionContext context) {
        try {
            return Optional.of(objectMapper.get().valueToTree(object));
        } catch (IllegalArgumentException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
