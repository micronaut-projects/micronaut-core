package io.micronaut.management.endpoint.env;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;

import java.util.*;

/**
 * TODO: javadoc
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Endpoint(EnvEndpoint.NAME)
public class EnvEndpoint {

    public final static String NAME = "env";

    private final Environment environment;

    public EnvEndpoint(Environment environment) {
        this.environment = environment;
    }

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
        propertySource.forEach(k -> properties.put(k, propertySource.get(k)));
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
}
