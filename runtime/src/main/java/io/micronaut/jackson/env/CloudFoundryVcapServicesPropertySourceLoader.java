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
package io.micronaut.jackson.env;

import com.fasterxml.jackson.core.JsonParseException;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.exceptions.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * <p>A {@link io.micronaut.context.env.PropertySourceLoader} that reads from the environment variable VCAP_SERVICES
 * which is used by CloudFoundry.</p>
 *
 * @author Fabian Nonnenmacher
 * @since 2.0
 */
public class CloudFoundryVcapServicesPropertySourceLoader extends EnvJsonPropertySourceLoader {

    /**
     * Position for the system property source loader in the chain.
     */
    public static final int POSITION = EnvJsonPropertySourceLoader.POSITION + 11;

    private static final String VCAP_SERVICES = "VCAP_SERVICES";

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    protected String getEnvValue() {
        return System.getenv(VCAP_SERVICES);
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        try {
            Map<String, Object> map = readJsonAsMap(input);
            processVcapServices(finalMap, map);
        } catch (JsonParseException e) {
            throw new ConfigurationException("Could not parse '" + VCAP_SERVICES + "': " + e.getMessage(), e);
        }
    }

    private void processVcapServices(Map<String, Object> finalMap, Map<String, Object> vcapServices) {
        if (vcapServices != null) {
            for (Object services : vcapServices.values()) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) services;
                for (Object object : list) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> service = (Map<String, Object>) object;
                    String key = (String) service.get("name");
                    if (key == null) {
                        key = (String) service.get("label");
                    }
                    processMap(finalMap, service, "vcap.services." + key + ".");
                }
            }
        }
    }

    @Override
    protected MapPropertySource createPropertySource(String name, Map<String, Object> map, int order) {
        return super.createPropertySource("cloudfoundry-vcap-services", map, order);
    }
}
