/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.reflect.GenericTypeUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Factory bean for creating the Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ObjectMapperFactory {

    /**
     * Name for Micronaut module.
     */
    public static final String MICRONAUT_MODULE = "micronaut";

    @Inject
    // have to be fully qualified due to JDK Module type
    protected com.fasterxml.jackson.databind.Module[] jacksonModules = new com.fasterxml.jackson.databind.Module[0];

    @Inject
    protected JsonSerializer[] serializers = new JsonSerializer[0];

    @Inject
    protected JsonDeserializer[] deserializers = new JsonDeserializer[0];

    @Inject
    protected BeanSerializerModifier[] beanSerializerModifiers = new BeanSerializerModifier[0];

    @Inject
    protected BeanDeserializerModifier[] beanDeserializerModifiers = new BeanDeserializerModifier[0];

    /**
     * Builds the core Jackson {@link ObjectMapper} from the optional configuration and {@link JsonFactory}.
     *
     * @param jacksonConfiguration The configuration
     * @param jsonFactory          The JSON factory
     * @return The {@link ObjectMapper}
     */
    @Bean
    @Singleton
    public ObjectMapper objectMapper(Optional<JacksonConfiguration> jacksonConfiguration,
                                     Optional<JsonFactory> jsonFactory) {

        ObjectMapper objectMapper = jsonFactory
            .map(ObjectMapper::new)
            .orElseGet(ObjectMapper::new);

        objectMapper.findAndRegisterModules();
        objectMapper.registerModules(jacksonModules);
        SimpleModule module = new SimpleModule(MICRONAUT_MODULE);
        for (JsonSerializer serializer : serializers) {
            Class<? extends JsonSerializer> type = serializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if (annotation != null) {
                Class[] value = annotation.value();
                for (Class aClass : value) {
                    module.addSerializer(aClass, serializer);
                }
            } else {
                Optional<Class> targetType = GenericTypeUtils.resolveSuperGenericTypeArgument(type);
                if (targetType.isPresent()) {
                    module.addSerializer(targetType.get(), serializer);
                } else {
                    module.addSerializer(serializer);
                }
            }
        }

        for (JsonDeserializer deserializer : deserializers) {
            Class<? extends JsonDeserializer> type = deserializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if (annotation != null) {
                Class[] value = annotation.value();
                for (Class aClass : value) {
                    module.addDeserializer(aClass, deserializer);
                }
            } else {
                Optional<Class> targetType = GenericTypeUtils.resolveSuperGenericTypeArgument(type);
                targetType.ifPresent(aClass -> module.addDeserializer(aClass, deserializer));
            }
        }
        objectMapper.registerModule(module);

        for (BeanSerializerModifier beanSerializerModifier : beanSerializerModifiers) {
            objectMapper.setSerializerFactory(
                objectMapper.getSerializerFactory().withSerializerModifier(
                    beanSerializerModifier
                ));
        }

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonConfiguration.ifPresent((configuration) -> {
            JsonInclude.Include include = configuration.getSerializationInclusion();
            if (include != null) {
                objectMapper.setSerializationInclusion(include);
            }
            String dateFormat = configuration.getDateFormat();
            if (dateFormat != null) {
                objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));
            }
            Locale locale = configuration.getLocale();
            if (locale != null) {
                objectMapper.setLocale(locale);
            }
            TimeZone timeZone = configuration.getTimeZone();
            if (timeZone != null) {
                objectMapper.setTimeZone(timeZone);
            }

            configuration.getSerializationSettings().forEach(objectMapper::configure);
            configuration.getDeserializationSettings().forEach(objectMapper::configure);
            configuration.getMapperSettings().forEach(objectMapper::configure);
            configuration.getParserSettings().forEach(objectMapper::configure);
            configuration.getGeneratorSettings().forEach(objectMapper::configure);
        });

        return objectMapper;
    }
}
