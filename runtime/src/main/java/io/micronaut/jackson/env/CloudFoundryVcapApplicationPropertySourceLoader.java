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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link io.micronaut.context.env.PropertySourceLoader} that reads from the environment variable VCAP_APPLICATION
 * which is used by CloudFoundry.</p>
 *
 * @author Fabian Nonnenmacher
 * @since 2.0
 */
public class CloudFoundryVcapApplicationPropertySourceLoader extends EnvJsonPropertySourceLoader {

    /**
     * Position for the system property source loader in the chain.
     */
    public static final int POSITION = EnvJsonPropertySourceLoader.POSITION + 10;

    private static final String VCAP_APPLICATION = "VCAP_APPLICATION";

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Set<String> getExtensions() {
        return Collections.emptySet();
    }

    @Override
    protected String getEnvValue() {
        return System.getenv(VCAP_APPLICATION);
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        try {
            Map<String, Object> map = readJsonAsMap(input);
            processMap(finalMap, map, "vcap.application.");
        } catch (JsonParseException e) {
            throw new ConfigurationException("Could not parse '" + VCAP_APPLICATION + "'." + e.getMessage(), e);
        }
    }

    @Override
    protected MapPropertySource createPropertySource(String name, Map<String, Object> map, int order) {
        return super.createPropertySource("cloudfoundry-vcap-application", map, order);
    }
}
