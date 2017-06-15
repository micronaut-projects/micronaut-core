package org.particleframework.context.env.groovy;

import org.particleframework.config.ConfigurationException;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.MapPropertySource;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.env.PropertySourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads properties from a Groovy script
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader implements PropertySourceLoader {
    @Override
    public Optional<PropertySource> load(Environment environment) {
        Map<String,Object> finalMap = new LinkedHashMap<>();
        loadProperties(environment, "application.groovy", finalMap);
        loadProperties(environment, "application-"+environment.getName()+".groovy", finalMap);
        if(!finalMap.isEmpty()) {
            MapPropertySource newPropertySource = new MapPropertySource(finalMap);
            return Optional.of(newPropertySource);
        }

        return Optional.empty();
    }

    private void loadProperties(Environment environment, String fileName, Map<String, Object> finalMap) {
        Optional<InputStream> config = environment.getResourceAsStream(fileName);
        ConfigurationEvaluator evaluator = new ConfigurationEvaluator();
        if(config.isPresent()) {
            try(InputStream input = config.get()) {
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
}
