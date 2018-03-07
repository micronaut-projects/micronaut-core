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
package io.micronaut.jackson.env;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.AbstractPropertySourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>A {@link PropertySourceLoader} that reads <tt>application.json</tt> files if they exist</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonPropertySourceLoader extends AbstractPropertySourceLoader {

    public static final String FILE_EXTENSION = "json";

    @Override
    protected String getFileExtension() {
        return FILE_EXTENSION;
    }

    @Override
    protected void processInput(InputStream input, Map<String, Object> finalMap) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());
        TypeFactory factory = TypeFactory.defaultInstance();
        MapType mapType = factory.constructMapType(LinkedHashMap.class, String.class, Object.class);

        Map<String,Object> map = objectMapper.readValue(input, mapType);
        processMap(finalMap, map, "");
    }
}
