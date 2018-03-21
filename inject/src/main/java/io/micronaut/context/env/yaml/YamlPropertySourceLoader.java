package io.micronaut.context.env.yaml;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Loads properties from a YML file
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class YamlPropertySourceLoader extends AbstractPropertySourceLoader {

    private static final String YAML_CLASS_NAME = "org.yaml.snakeyaml.Yaml";

    @Override
    public boolean isEnabled() {
        return ClassUtils.isPresent(YAML_CLASS_NAME, getClass().getClassLoader());
    }

    @Override
    public Set<String> getExtensions() {
        return CollectionUtils.setOf("yml", "yaml");
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        Yaml yaml = new Yaml();
        Iterable<Object> objects = yaml.loadAll(input);
        for (Object object : objects) {
            if(object instanceof Map) {
                Map map = (Map) object;
                String prefix = "";
                processMap(finalMap, map, prefix);
            }
        }
    }

}
