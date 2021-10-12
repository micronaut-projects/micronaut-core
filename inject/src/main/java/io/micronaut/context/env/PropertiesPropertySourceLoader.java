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
package io.micronaut.context.env;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Default load that handles Java properties files.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertiesPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for property source loader.
     */
    public static final String PROPERTIES_EXTENSION = "properties";

    @Override
    public Set<String> getExtensions() {
        return Collections.singleton(PROPERTIES_EXTENSION);
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        Properties props = new Properties();
        props.load(input);
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            finalMap.put(entry.getKey().toString(), entry.getValue());
        }
    }
}
