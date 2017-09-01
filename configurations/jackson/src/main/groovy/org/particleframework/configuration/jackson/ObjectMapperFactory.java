package org.particleframework.configuration.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Factory bean for creating the Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ObjectMapperFactory {


    @Inject
    protected Module[] jacksonModules = new Module[0];

    /**
     * Builds the core Jackson {@link ObjectMapper} from the optional configuration and {@link JsonFactory}
     *
     * @param jacksonConfiguration The configuration
     * @param jsonFactory The JSON factory
     * @return The {@link ObjectMapper}
     */
    @Bean
    ObjectMapper objectMapper(Optional<JacksonConfiguration> jacksonConfiguration,
                              Optional<JsonFactory> jsonFactory) {

        ObjectMapper objectMapper = jsonFactory.map(ObjectMapper::new)
                                               .orElseGet(ObjectMapper::new);

        objectMapper.registerModules(jacksonModules);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonConfiguration.ifPresent((configuration)->{
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

    @Bean
    JsonTypeConverter jsonTypeConverter(ObjectMapper objectMapper) {
        return new JsonTypeConverter(objectMapper);
    }
}
