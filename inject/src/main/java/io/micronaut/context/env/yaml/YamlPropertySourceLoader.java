/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env.yaml;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.core.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Loads properties from a YML file.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class YamlPropertySourceLoader extends AbstractPropertySourceLoader {

    public YamlPropertySourceLoader() {
    }

    public YamlPropertySourceLoader(boolean logEnabled) {
        super(logEnabled);
    }

    @Override
    public boolean isEnabled() {
        return isSnakeYamlPresent();
    }

    @Override
    public Set<String> getExtensions() {
        return CollectionUtils.setOf("yml", "yaml");
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) {
        // workaround for Graal which returns null
        if (System.getProperty("java.runtime.name") == null) {
            System.setProperty("java.runtime.name", "Unknown");
        }

        Iterable<Object> objects = Wrapper.loadObjects(input);
        Iterator<Object> i = objects.iterator();
        if (i.hasNext()) {
            while (i.hasNext()) {
                Object object = i.next();
                if (object instanceof Map) {
                    Map map = (Map) object;
                    log.trace("Processing YAML: {}", map);
                    String prefix = "";
                    processMap(finalMap, map, prefix);
                }
            }
        } else {
            log.trace("PropertySource [{}] produced no YAML content", name);
        }
    }

    private static boolean isSnakeYamlPresent() {
        try {
            Class<Yaml> yamlClass = Yaml.class;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static class Wrapper {
        // in nested class to prevent NCDFE

        private static Iterable<Object> loadObjects(InputStream input) {
            Yaml yaml = new Yaml(new CustomSafeConstructor());
            return yaml.loadAll(input);
        }
    }
}
