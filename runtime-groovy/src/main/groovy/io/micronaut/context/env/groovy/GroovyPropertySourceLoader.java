package io.micronaut.context.env.groovy;

import io.micronaut.context.env.*;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.value.ValueException;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.value.ValueException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads properties from a Groovy script
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader implements PropertySourceLoader, Ordered {

    @Override
    public int getOrder() {
        return EnvironmentPropertySource.POSITION - 100;
    }

    @Override
    public Optional<PropertySource> load(String resourceName, Environment environment, String environmentName) {
        Map<String,Object> finalMap = new LinkedHashMap<>();
        String ext =  ".groovy";
        String fileName = resourceName;
        if(environmentName != null) {
            fileName += "-" + environmentName;
        }
        String qualifiedName = fileName;
        fileName += ext;

        loadProperties(environment, fileName, finalMap);
        int order = this.getOrder();
        if(environmentName != null) {
            order++; // higher precedence than the default
        }

        if(!finalMap.isEmpty()) {
            int finalOrder = order;
            MapPropertySource newPropertySource = new MapPropertySource(qualifiedName, finalMap) {
                @Override
                public int getOrder() {
                    return finalOrder;
                }
            };
            return Optional.of(newPropertySource);
        }

        return Optional.empty();
    }

    private void loadProperties(Environment environment, String fileName, Map<String, Object> finalMap) {
        Stream<URL> urls = environment.getResources(fileName);

        urls.forEach(res -> {
            ConfigurationEvaluator evaluator = new ConfigurationEvaluator();
            String path = res.getPath();
            if(!path.contains("src/main/groovy")) {
                try(InputStream input = res.openStream()) {
                    Map<String, Object> configurationValues = evaluator.evaluate(input);
                    if(!configurationValues.isEmpty()) {
                        finalMap.putAll(configurationValues);
                    }
                }
                catch (IOException e){
                    throw new ValueException("I/O exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
                }
                catch (Throwable e) {
                    throw new ValueException("Exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
                }
            }

          }
        );
    }
}
