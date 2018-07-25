/*
 * Copyright 2017-2018 original authors
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
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Loads properties from a YML file.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class YamlPropertySourceLoader extends AbstractPropertySourceLoader {

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
        Yaml yaml = new Yaml();
        Iterable<Object> objects = yaml.loadAll(input);
        for (Object object : objects) {
            if (object instanceof Map) {
                Map map = (Map) object;
                String prefix = "";
                processMap(finalMap, map, prefix);
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
