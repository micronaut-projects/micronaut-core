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
package io.micronaut.discovery.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;

@Controller("/")
@Requires(property = MockSpringCloudConfigServer.ENABLED)
public class MockSpringCloudConfigServer {

    public static final String ENABLED = "enable.mock.spring-cloud-config";

    final static Logger LOGGER = LoggerFactory.getLogger(MockSpringCloudConfigServer.class);

    @Get("/{applicationName}{/profiles}")
    @Produces(single = true)
    public Publisher<ConfigServerResponse> readValues(@NonNull String applicationName,
                                                      @Nullable String profiles) {
        return getConfigServerResponse(applicationName, profiles, null);
    }

    @Get("/{applicationName}{/profiles}{/label}")
    @Produces(single = true)
    public Publisher<ConfigServerResponse> readValues(@NonNull String applicationName,
                                                      @Nullable String profiles,
                                                      @Nullable String label) {
        return getConfigServerResponse(applicationName, profiles, label);
    }

    private Publisher<ConfigServerResponse> getConfigServerResponse(
            String applicationName, String profiles, String label)
    {
        String[] profilesArray = profiles != null ? profiles.split(",") : new String[0];
        ConfigServerResponse configServerResponse = new ConfigServerResponse();
        configServerResponse.setName(applicationName);
        configServerResponse.setProfiles(profilesArray);
        configServerResponse.setLabel(label);
        configServerResponse.setState(null);
        configServerResponse.setVersion(null);
        List<String> profileList = Arrays.asList(profilesArray);
        Collections.reverse(profileList);

        for (String profile: profileList) {

            if (profile.equals("second") && applicationName.equals("myapp")) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("config-secret-1", 1);
                ConfigServerPropertySource configServerPropertySourceDev = new ConfigServerPropertySource(applicationName + "[" + profile + "]", properties) {};
                configServerResponse.getPropertySources().add(configServerPropertySourceDev);

                properties = new HashMap<>();
                properties.put("config-secret-1", 2);
                properties.put("config-secret-2", 1);

                configServerPropertySourceDev = new ConfigServerPropertySource("application[" + profile + "]", properties) {};
                configServerResponse.getPropertySources().add(configServerPropertySourceDev);
            }

            if (profile.equals("first") && applicationName.equals("myapp")) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("config-secret-1", 3);
                properties.put("config-secret-2", 2);
                properties.put("config-secret-3", 1);
                ConfigServerPropertySource configServerPropertySourceDev = new ConfigServerPropertySource(applicationName + "[" + profile + "]", properties) {
                };
                configServerResponse.getPropertySources().add(configServerPropertySourceDev);

                properties = new HashMap<>();
                properties.put("config-secret-1", 4);
                properties.put("config-secret-2", 3);
                properties.put("config-secret-3", 2);
                properties.put("config-secret-4", 1);

                configServerPropertySourceDev = new ConfigServerPropertySource("application[" + profile + "]", properties) {
                };
                configServerResponse.getPropertySources().add(configServerPropertySourceDev);
            }
        }

        if (applicationName.equals("myapp")) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("config-secret-1", 5);
            properties.put("config-secret-2", 4);
            properties.put("config-secret-3", 3);
            properties.put("config-secret-4", 2);
            properties.put("config-secret-5", 1);
            ConfigServerPropertySource configServerPropertySourceDev = new ConfigServerPropertySource(applicationName, properties) {};
            configServerResponse.getPropertySources().add(configServerPropertySourceDev);

            properties = new HashMap<>();
            properties.put("config-secret-1", 6);
            properties.put("config-secret-2", 5);
            properties.put("config-secret-3", 4);
            properties.put("config-secret-4", 3);
            properties.put("config-secret-5", 2);
            properties.put("config-secret-6", 1);

            configServerPropertySourceDev = new ConfigServerPropertySource("application", properties) {};
            configServerResponse.getPropertySources().add(configServerPropertySourceDev);
        }

        return Publishers.just(configServerResponse);
    }
}
