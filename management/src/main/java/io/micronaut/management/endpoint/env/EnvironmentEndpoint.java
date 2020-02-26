/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.endpoint.env;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link Endpoint} that displays information about the environment and its property sources.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.2.0
 */
@Endpoint(EnvironmentEndpoint.NAME)
public class EnvironmentEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "env";

    public static final String[] PROPERTY_NAMES_TO_MASK = new String[] {
            "password", "credential", "certificate", "key", "secret", "token"
    };

    private final Environment environment;
    private final List<Pattern> maskPatterns;

    /**
     * @param environment The {@link Environment}
     */
    public EnvironmentEndpoint(Environment environment) {
        this.environment = environment;
        this.maskPatterns = Arrays.stream(PROPERTY_NAMES_TO_MASK)
                .map(s -> Pattern.compile(".*" + s + ".*", Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
    }

    /**
     * @return The environment information as a map with the following keys: activeEnvironments, packages and
     * propertySources.
     */
    @Read
    public Map<String, Object> getEnvironmentInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeEnvironments", environment.getActiveNames());
        result.put("packages", environment.getPackages());
        Collection<Map<String, Object>> propertySources = new ArrayList<>();
        environment.getPropertySources()
                .stream()
                .sorted(Comparator.comparing(PropertySource::getOrder))
                .forEach(ps -> propertySources.add(buildPropertySourceInfo(ps)));
        result.put("propertySources", propertySources);
        return result;
    }

    /**
     * @param propertySourceName The {@link PropertySource} name
     * @return a map with all the properties defined in the property source if it exists; null otherwise.
     */
    @Read
    public Map<String, Object> getProperties(@Selector String propertySourceName) {
        return environment.getPropertySources()
                .stream()
                .filter(ps -> ps.getName().equals(propertySourceName))
                .findFirst()
                .map(this::buildPropertySourceInfo)
                .orElse(null);
    }

    private Map<String, Object> getAllProperties(PropertySource propertySource) {
        Map<String, Object> properties = new LinkedHashMap<>();
        propertySource.forEach(k -> properties.put(k, maskProperty(k, propertySource.get(k))));
        return properties;
    }

    private Map<String, Object> buildPropertySourceInfo(PropertySource propertySource) {
        Map<String, Object> propertySourceInfo = new LinkedHashMap<>();
        propertySourceInfo.put("name", propertySource.getName());
        propertySourceInfo.put("order", propertySource.getOrder());
        propertySourceInfo.put("convention", propertySource.getConvention().name());
        propertySourceInfo.put("properties", getAllProperties(propertySource));
        return propertySourceInfo;
    }

    private Object maskProperty(String key, Object value) {
        for (Pattern pattern : this.maskPatterns) {
            if (pattern.matcher(key).matches()) {
                return "*****";
            }
        }
        return value;
    }
}
