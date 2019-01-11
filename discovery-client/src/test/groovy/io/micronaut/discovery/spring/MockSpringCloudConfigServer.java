package io.micronaut.discovery.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigOperations;
import io.micronaut.discovery.spring.config.client.response.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.response.ConfigServerResponse;
import io.micronaut.http.annotation.Controller;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@Controller("/")
@Requires(property = MockSpringCloudConfigServer.ENABLED)
public class MockSpringCloudConfigServer implements SpringCloudConfigOperations {

    public static final String ENABLED = "enable.mock.spring-cloud-config";

    final static Logger LOGGER = LoggerFactory.getLogger(MockSpringCloudConfigServer.class);
    @Override
    public @Nonnull Publisher<ConfigServerResponse> readValues(@Nonnull String applicationName, @Nullable String profiles) {
        String[] profilesArray = profiles != null ? profiles.split(",") : new String[0];
        ConfigServerResponse configServerResponse = new ConfigServerResponse();
        configServerResponse.setName(applicationName);
        configServerResponse.setProfiles(profilesArray);
        configServerResponse.setLabel(null);
        configServerResponse.setState(null);
        configServerResponse.setVersion(null);
        List<String> list = Arrays.asList(profilesArray);
        List<String> newList = new ArrayList<>(list);
        Collections.reverse(newList);
        for (String profile: newList) {
            Map<String, Object> sourceDev = new HashMap<>();
            sourceDev.put("environment-name", profile);
            ConfigServerPropertySource configServerPropertySourceDev = new ConfigServerPropertySource("classpath:/configclientdemo-" + profile + ".properties", sourceDev) {};
            configServerResponse.getPropertySources().add(configServerPropertySourceDev);

        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            LOGGER.info(objectMapper.writeValueAsString(configServerResponse));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Publishers.just(configServerResponse);
    }
}