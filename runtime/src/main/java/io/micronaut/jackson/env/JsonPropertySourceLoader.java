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
package io.micronaut.jackson.env;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.env.AbstractPropertySourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link io.micronaut.context.env.PropertySourceLoader} that reads <tt>application.json</tt> files if they exist.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for property source loader.
     */
    public static final String FILE_EXTENSION = "json";

    @Override
    public Set<String> getExtensions() {
        return Collections.singleton(FILE_EXTENSION);
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        Map<String, Object> map = readJsonAsMap(input);
        processMap(finalMap, map, "");
    }

    /**
     * @param input    The input stream
     * @throws IOException If the input stream doesn't exist
     *
     * @return map representation of the json
     */
    protected Map<String, Object> readJsonAsMap(InputStream input) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());
        TypeFactory factory = TypeFactory.defaultInstance();
        MapType mapType = factory.constructMapType(LinkedHashMap.class, String.class, Object.class);

        return objectMapper.readValue(input, mapType);
    }
}
