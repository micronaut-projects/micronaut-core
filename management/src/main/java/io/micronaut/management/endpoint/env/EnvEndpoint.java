package io.micronaut.management.endpoint.env;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

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

    private Environment environment;

    public EnvEndpoint(Environment environment) {
        this.environment = environment;
    }

    @Read
    public Map<String, Object> getEnvironmentInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeEnvironments", environment.getActiveNames());
        result.put("packages", environment.getPackages());
        Collection<Map<String, Object>> propertySources = new ArrayList<>();
        environment.getPropertySources()
                .stream()
                .sorted(Comparator.comparing(PropertySource::getOrder))
                .forEach(ps -> {
                    Map<String, Object> propertySource = new HashMap<>();
                    propertySource.put("name", ps.getName());
                    propertySource.put("order", ps.getOrder());
                    Map<String, Object> properties = new HashMap<>();
                    ps.forEach(k -> properties.put(k, ps.get(k)));
                    propertySource.put("properties", properties);
                    propertySources.add(propertySource);
                });
        result.put("propertySources", propertySources);
        return result;
    }


}
