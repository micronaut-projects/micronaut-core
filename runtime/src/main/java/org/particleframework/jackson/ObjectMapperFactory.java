package org.particleframework.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.Type;
import org.particleframework.core.reflect.GenericTypeUtils;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Factory bean for creating the Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ObjectMapperFactory {


    public static final String PARTICLE_MODULE = "particle";

    @Inject
    protected Module[] jacksonModules = new Module[0];

    @Inject
    protected JsonSerializer[] serializers = new JsonSerializer[0];

    @Inject
    protected JsonDeserializer[] deserializers = new JsonDeserializer[0];

    @Inject
    protected BeanSerializerModifier[] beanSerializerModifiers = new BeanSerializerModifier[0];

    /**
     * Builds the core Jackson {@link ObjectMapper} from the optional configuration and {@link JsonFactory}
     *
     * @param jacksonConfiguration The configuration
     * @param jsonFactory The JSON factory
     * @return The {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper(Optional<JacksonConfiguration> jacksonConfiguration,
                              Optional<JsonFactory> jsonFactory) {

        ObjectMapper objectMapper = jsonFactory.map(ObjectMapper::new)
                                               .orElseGet(ObjectMapper::new);

        objectMapper.findAndRegisterModules();
        objectMapper.registerModules(jacksonModules);
        SimpleModule module = new SimpleModule(PARTICLE_MODULE);
        for (JsonSerializer serializer : serializers) {
            Class<? extends JsonSerializer> type = serializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if(annotation != null) {
                Class[] value = annotation.value();
                for (Class aClass : value) {
                    module.addSerializer(aClass, serializer);
                }
            }
            else {
                Optional<Class> targetType = GenericTypeUtils.resolveSuperGenericTypeArgument(type);
                if(targetType.isPresent()) {
                    module.addSerializer(targetType.get(), serializer);
                }
                else {
                    module.addSerializer(serializer);
                }
            }

        }
        for (JsonDeserializer deserializer : deserializers) {
            Class<? extends JsonDeserializer> type = deserializer.getClass();
            Type annotation = type.getAnnotation(Type.class);
            if(annotation != null) {
                Class[] value = annotation.value();
                for (Class aClass : value) {
                    module.addDeserializer(aClass, deserializer);
                }
            }
            else {
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
        jacksonConfiguration.ifPresent((configuration)->{
            JsonInclude.Include include = configuration.getSerializationInclusion();
            if (include != null) {
                objectMapper.setSerializationInclusion(include);
            }
            String dateFormat = configuration.getDateFormat();
            if(dateFormat != null) {
                objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));
            }
            Locale locale = configuration.getLocale();
            if(locale != null) {
                objectMapper.setLocale(locale);
            }
            TimeZone timeZone = configuration.getTimeZone();
            if(timeZone != null) {
                objectMapper.setTimeZone(timeZone);
            }

            configuration.getSerializationSettings()
                         .forEach(objectMapper::configure);

            configuration.getDeserializationSettings()
                         .forEach(objectMapper::configure);

            configuration.getMapperSettings()
                         .forEach(objectMapper::configure);

            configuration.getParserSettings()
                         .forEach(objectMapper::configure);

            configuration.getGeneratorSettings()
                         .forEach(objectMapper::configure);
        });


        return objectMapper;
    }

}
