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
package io.micronaut.context.env.yaml;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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

    private static final Logger LOG = LoggerFactory.getLogger(YamlPropertySourceLoader.class);

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

        Yaml yaml = new Yaml(new SafeConstructor());
        Iterable<Object> objects = yaml.loadAll(input);
        Iterator<Object> i = objects.iterator();
        if (i.hasNext()) {
            while (i.hasNext()) {
                Object object = i.next();
                if (object instanceof Map) {
                    Map map = (Map) object;
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Processing YAML: {}", map);
                    }
                    String prefix = "";
                    processMap(finalMap, map, prefix);
                }
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PropertySource [{}] produced no YAML content", name);
            }
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

}
