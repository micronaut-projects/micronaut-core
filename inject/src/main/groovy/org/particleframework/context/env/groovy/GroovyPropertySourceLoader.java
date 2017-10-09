package org.particleframework.context.env.groovy;

import org.particleframework.config.ConfigurationException;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.MapPropertySource;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.env.PropertySourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads properties from a Groovy script
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader implements PropertySourceLoader {

    @Override
    public Optional<PropertySource> load(String name, Environment environment) {
        Map<String,Object> finalMap = new LinkedHashMap<>();
        loadProperties(environment, name + ".groovy", finalMap);
        loadProperties(environment, name + "-"+environment.getName()+".groovy", finalMap);
        if(!finalMap.isEmpty()) {
            MapPropertySource newPropertySource = new MapPropertySource(finalMap);
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
                    throw new ConfigurationException("I/O exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
                }
                catch (Throwable e) {
                    throw new ConfigurationException("Exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
                }
            }

          }
        );
    }
}
