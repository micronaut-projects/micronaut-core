/*
 * Copyright 2017 original authors
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
package org.particleframework.context.env;

import org.particleframework.config.ConfigurationException;
import org.particleframework.core.util.Toggleable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractPropertySourceLoader implements PropertySourceLoader, Toggleable {
    @Override
    public Optional<PropertySource> load(String name, Environment environment) {
        if(isEnabled()) {
            Map<String,Object> finalMap = new LinkedHashMap<>();
            String ext = getFileExtension();
            loadProperties(environment, name + "." + ext, finalMap);
            loadProperties(environment, name + "-"+environment.getName()+"." + ext, finalMap);
            if(!finalMap.isEmpty()) {
                MapPropertySource newPropertySource = new MapPropertySource(finalMap);
                return Optional.of(newPropertySource);
            }
        }

        return Optional.empty();
    }

    /**
     * @return The file extension to process
     */
    protected abstract String getFileExtension();

    private void loadProperties(Environment environment, String fileName, Map<String, Object> finalMap) {
        Optional<InputStream> config = environment.getResourceAsStream(fileName);
        if(config.isPresent()) {
            try(InputStream input = config.get()) {
                processInput(input, finalMap);
            }
            catch (IOException e){
                throw new ConfigurationException("I/O exception occurred reading ["+fileName+"]: " + e.getMessage(), e);
            }
        }
    }

    protected abstract void processInput(InputStream input, Map<String, Object> finalMap) throws IOException;

    protected void processMap(Map<String, Object> finalMap, Map map, String prefix) {
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
