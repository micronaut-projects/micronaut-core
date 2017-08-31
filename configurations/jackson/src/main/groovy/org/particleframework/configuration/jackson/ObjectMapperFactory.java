package org.particleframework.configuration.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Optional;

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

        ObjectMapper objectMapper = jsonFactory.isPresent() ? new ObjectMapper(jsonFactory.get()) : new ObjectMapper();

        objectMapper.registerModules(jacksonModules);
        jacksonConfiguration.ifPresent((configuration)->{
            String dateFormat = configuration.getDateFormat();
            if(dateFormat != null) {
                objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));
            }

            configuration.getSerializationSettings()
                         .forEach(objectMapper::configure);

            configuration.getDeserializationSettings()
                         .forEach(objectMapper::configure);

        });

        return objectMapper;
    }
}
