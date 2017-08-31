package org.particleframework.configuration.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Collections;
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

    @Bean
    ObjectMapper objectMapper(Optional<JacksonConfiguration> optionalConfiguration,
                              Optional<JsonFactory> jsonFactory) {
        JacksonConfiguration jacksonConfiguration = optionalConfiguration.orElse(new JacksonConfiguration());

        ObjectMapper objectMapper = jsonFactory.isPresent() ? new ObjectMapper(jsonFactory.get()) : new ObjectMapper();

        objectMapper.registerModules(jacksonModules);
        String dateFormat = jacksonConfiguration.getDateFormat();
        if(dateFormat != null) {
            objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));
        }
        return objectMapper;
    }
}
