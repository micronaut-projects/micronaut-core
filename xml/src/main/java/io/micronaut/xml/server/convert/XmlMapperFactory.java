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
package io.micronaut.xml.server.convert;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.jackson.JacksonConfiguration;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Factory bean for creating the Jackson {@link XmlMapper}.
 *
 * The factory mostly duplicates {@link io.micronaut.jackson.ObjectMapperFactory} with the only difference that it creates
 * object mappers dedicated for xml processing and avoid some json specific configuration that might have been done inside
 * of {@link io.micronaut.jackson.ObjectMapperFactory}.
 *
 * @author Sergey Vishnyakov
 * @since 1.3.0
 */
@Factory
@BootstrapContextCompatible
public class XmlMapperFactory {

    @Inject
    // has to be fully qualified due to JDK Module type
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
     * Builds the core Jackson {@link ObjectMapper} from the optional configuration and {@link com.fasterxml.jackson.core.JsonFactory}.
     *
     * @param jacksonConfiguration The configuration
     * @return The {@link ObjectMapper}
     */
    @Singleton
    @BootstrapContextCompatible
    @Named("xml")
    public XmlMapper xmlMapper(@Nullable JacksonConfiguration jacksonConfiguration) {

        XmlMapper objectMapper = new XmlMapper();

        final boolean hasConfiguration = jacksonConfiguration != null;
        if (!hasConfiguration || jacksonConfiguration.isModuleScan()) {
            objectMapper.findAndRegisterModules();
        }
        objectMapper.registerModules(jacksonModules);

        for (BeanSerializerModifier beanSerializerModifier : beanSerializerModifiers) {
            objectMapper.setSerializerFactory(
                    objectMapper.getSerializerFactory().withSerializerModifier(
                            beanSerializerModifier
                    ));
        }

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);

        if (hasConfiguration) {

            ObjectMapper.DefaultTyping defaultTyping = jacksonConfiguration.getDefaultTyping();
            if (defaultTyping != null) {
                objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(), defaultTyping);
            }

            JsonInclude.Include include = jacksonConfiguration.getSerializationInclusion();
            if (include != null) {
                objectMapper.setSerializationInclusion(include);
            }
            String dateFormat = jacksonConfiguration.getDateFormat();
            if (dateFormat != null) {
                objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));
            }
            Locale locale = jacksonConfiguration.getLocale();
            if (locale != null) {
                objectMapper.setLocale(locale);
            }
            TimeZone timeZone = jacksonConfiguration.getTimeZone();
            if (timeZone != null) {
                objectMapper.setTimeZone(timeZone);
            }
            PropertyNamingStrategy propertyNamingStrategy = jacksonConfiguration.getPropertyNamingStrategy();
            if (propertyNamingStrategy != null) {
                objectMapper.setPropertyNamingStrategy(propertyNamingStrategy);
            }

            jacksonConfiguration.getSerializationSettings().forEach(objectMapper::configure);
            jacksonConfiguration.getDeserializationSettings().forEach(objectMapper::configure);
            jacksonConfiguration.getMapperSettings().forEach(objectMapper::configure);
            jacksonConfiguration.getParserSettings().forEach(objectMapper::configure);
            jacksonConfiguration.getGeneratorSettings().forEach(objectMapper::configure);
        }
        return objectMapper;
    }
}
