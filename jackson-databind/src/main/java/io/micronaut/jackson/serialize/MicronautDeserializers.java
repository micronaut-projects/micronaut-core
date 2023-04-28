package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;

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
            return new ConvertibleValuesDeserializer<>(conversionService, valueType);
        }

        return super.findBeanDeserializer(type, config, beanDesc);
    }
}
