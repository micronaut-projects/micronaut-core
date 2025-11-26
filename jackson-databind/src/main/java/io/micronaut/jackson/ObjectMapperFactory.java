/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Type;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.serialize.MicronautDeserializers;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import tools.jackson.core.JsonParser;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.DefaultBaseTypeLimitingValidator;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Factory bean for creating the Jackson {@link tools.jackson.databind.ObjectMapper}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@BootstrapContextCompatible
public class ObjectMapperFactory {

    /**
     * Name for Micronaut module.
     */
    public static final String MICRONAUT_MODULE = "micronaut";

    @Inject
    protected ConversionService conversionService;

    @Inject
    protected BeanContext beanContext;

    @Inject
    // have to be fully qualified due to JDK Module type
    protected JacksonModule[] jacksonModules = new JacksonModule[0];

    @Inject
    protected ValueSerializer[] serializers = new ValueSerializer[0];

    @Inject
    protected ValueDeserializer[] deserializers = new ValueDeserializer[0];

    @Inject
    protected ValueSerializerModifier[] beanSerializerModifiers = new ValueSerializerModifier[0];

    @Inject
    protected ValueDeserializerModifier[] beanDeserializerModifiers = new ValueDeserializerModifier[0];

    @Inject
    protected KeyDeserializer[] keyDeserializers = new KeyDeserializer[0];

    /**
     * Builds default Factory {@link JsonFactory} using properties from {@link JacksonConfiguration}.
     *
     * @param jacksonConfiguration The configuration
     * @return The {@link JsonFactory}
     */
    @Requires(beans = JacksonConfiguration.class)
    @Singleton
    @BootstrapContextCompatible
    public JsonFactory jsonFactory(JacksonConfiguration jacksonConfiguration) {
        final JsonFactoryBuilder jsonFactoryBuilder = JsonFactory.builder();
        jacksonConfiguration.getFactorySettings().forEach(jsonFactoryBuilder::configure);
        return jsonFactoryBuilder.build();
    }

    /**
     * Set additional serializers.
     *
     * @param serializers The serializers
     * @since 4.0
     */
    public void setSerializers(ValueSerializer... serializers) {
        this.serializers = serializers;
    }

    /**
     * Set additional deserializers.
     *
     * @param deserializers The deserializers
     * @since 4.0
     */
    public void setDeserializers(ValueDeserializer... deserializers) {
        this.deserializers = deserializers;
    }

    /**
     * Builds the core Jackson {@link ObjectMapper} from the optional configuration and {@link JsonFactory}.
     *
     * @param jacksonConfiguration The configuration
     * @param jsonFactory The JSON factory
     * @return The {@link ObjectMapper}
     */
    @Singleton
    @Primary
    @Named("json")
    @BootstrapContextCompatible
    public JsonMapper objectMapper(@Nullable JacksonConfiguration jacksonConfiguration,
                                     @Nullable JsonFactory jsonFactory) {
        JsonMapper.Builder builder = jsonFactory != null ? JsonMapper.builder(jsonFactory) : JsonMapper.builder();

        final boolean hasConfiguration = jacksonConfiguration != null;
        if (!hasConfiguration || jacksonConfiguration.isModuleScan()) {
            builder.findAndAddModules();
        }
        builder.addModules(jacksonModules);
        if (beanContext != null) {
            builder.typeFactory(builder.typeFactory().withClassLoader(beanContext.getClassLoader()));
        }

        SimpleModule module = new SimpleModule(MICRONAUT_MODULE);
        module.setDeserializers(new MicronautDeserializers(conversionService));

        for (ValueSerializer serializer : serializers) {
            Class<? extends ValueSerializer> type = serializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if (annotation != null) {
                Class<?>[] value = annotation.value();
                for (Class<?> aClass : value) {
                    module.addSerializer(aClass, serializer);
                }
            } else {
                Optional<Class<?>> targetType = GenericTypeUtils.resolveSuperGenericTypeArgument(type);
                if (targetType.isPresent()) {
                    module.addSerializer(targetType.get(), serializer);
                } else {
                    module.addSerializer(serializer);
                }
            }
        }

        for (ValueDeserializer deserializer : deserializers) {
            Class<? extends ValueDeserializer> type = deserializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if (annotation != null) {
                Class<?>[] value = annotation.value();
                for (Class<?> aClass : value) {
                    module.addDeserializer(aClass, deserializer);
                }
            } else {
                Optional<Class<?>> targetType = GenericTypeUtils.resolveSuperGenericTypeArgument(type);
                targetType.ifPresent(aClass -> module.addDeserializer(aClass, deserializer));
            }
        }

        if (hasConfiguration && jacksonConfiguration.isTrimStrings()) {
            module.addDeserializer(String.class, new StringDeserializer() {
                @Override
                public String deserialize(JsonParser p, DeserializationContext ctxt) {
                    String value = super.deserialize(p, ctxt);
                    return StringUtils.trimToNull(value);
                }
            });
        }

        for (KeyDeserializer keyDeserializer : keyDeserializers) {
            Class<? extends KeyDeserializer> type = keyDeserializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if (annotation != null) {
                Class<?>[] value = annotation.value();
                for (Class<?> clazz : value) {
                    module.addKeyDeserializer(clazz, keyDeserializer);
                }
            }
        }
        builder.addModule(module);

        for (ValueSerializerModifier beanSerializerModifier : beanSerializerModifiers) {
            builder.serializerFactory(
                builder.serializerFactory().withSerializerModifier(
                    beanSerializerModifier
                ));
        }

        builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);

        if (hasConfiguration) {

            DefaultTyping defaultTyping = jacksonConfiguration.getDefaultTyping();
            if (defaultTyping != null) {
                builder.activateDefaultTyping(new DefaultBaseTypeLimitingValidator(), defaultTyping);
            }

            JsonInclude.Include include = jacksonConfiguration.getSerializationInclusion();
            if (include != null) {
                builder.changeDefaultPropertyInclusion(oldInclude -> oldInclude.withValueInclusion(include).withContentInclusion(include));
            }
            String dateFormat = jacksonConfiguration.getDateFormat();
            if (dateFormat != null) {
                builder.defaultDateFormat(new SimpleDateFormat(dateFormat));
            }
            Locale locale = jacksonConfiguration.getLocale();
            if (locale != null) {
                builder.defaultLocale(locale);
            }
            TimeZone timeZone = jacksonConfiguration.getTimeZone();
            if (timeZone != null) {
                builder.defaultTimeZone(timeZone);
            }
            PropertyNamingStrategy propertyNamingStrategy = jacksonConfiguration.getPropertyNamingStrategy();
            if (propertyNamingStrategy != null) {
                builder.propertyNamingStrategy(propertyNamingStrategy);
            }

            jacksonConfiguration.getSerializationSettings().forEach(builder::configure);
            jacksonConfiguration.getDeserializationSettings().forEach(builder::configure);
            jacksonConfiguration.getMapperSettings().forEach(builder::configure);
            jacksonConfiguration.getParserSettings().forEach(builder::configure);
            jacksonConfiguration.getGeneratorSettings().forEach(builder::configure);
        }

        return builder.build();
    }
}
