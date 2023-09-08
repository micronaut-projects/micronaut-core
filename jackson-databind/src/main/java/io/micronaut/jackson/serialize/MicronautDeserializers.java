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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;

/**
 * Micronaut deserializers for jackson.
 */
@Internal
public class MicronautDeserializers extends SimpleDeserializers {
    private final ConversionService conversionService;

    public MicronautDeserializers(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public JsonDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException {
        if (type.getRawClass() == ConvertibleValues.class) {
            JavaType valueType = type.containedTypeOrUnknown(0);
            if (valueType.equals(TypeFactory.unknownType())) {
                valueType = null;
            }
            return new ConvertibleValuesDeserializer<>(conversionService, valueType);
        }

        return super.findBeanDeserializer(type, config, beanDesc);
    }
}
