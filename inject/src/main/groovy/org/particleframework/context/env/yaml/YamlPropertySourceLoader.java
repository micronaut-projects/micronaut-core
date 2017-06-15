package org.particleframework.context.env.yaml;

import org.particleframework.config.ConfigurationException;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.MapPropertySource;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.env.PropertySourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads properties from a YML file
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class YamlPropertySourceLoader implements PropertySourceLoader {
    @Override
    public Optional<PropertySource> load(Environment environment) {
        if(environment.isPresent("org.yaml.snakeyaml.Yaml")) {
            Map<String,Object> finalMap = new LinkedHashMap<>();
            loadProperties(environment, "application.yml", finalMap);
            loadProperties(environment, "application-"+environment.getName()+".yml", finalMap);
            if(!finalMap.isEmpty()) {
                MapPropertySource newPropertySource = new MapPropertySource(finalMap);
                return Optional.of(newPropertySource);
            }
        }

        return Optional.empty();
    }

    private void loadProperties(Environment environment, String fileName, Map<String, Object> finalMap) {
        Yaml yaml = new Yaml();
        Optional<InputStream> config = environment.getResourceAsStream(fileName);
        if(config.isPresent()) {
            try(InputStream input = config.get()) {
                Iterable<Object> objects = yaml.loadAll(input);
                for (Object object : objects) {
                    if(object instanceof Map) {
                        Map map = (Map) object;
                        String prefix = "";
                        processMap(finalMap, map, prefix);
                    }
                }
            }
            catch (IOException e){
                throw new ConfigurationException("I/O exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
            }
        }
    }

    private void processMap(Map<String, Object> finalMap, Map map, String prefix) {
        for (Object o : map.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if(value instanceof Map) {
                processMap(finalMap, (Map) value, prefix + key + '.');
            }
            else {
                finalMap.put(prefix + key, value);
            }
        }
    }
}
