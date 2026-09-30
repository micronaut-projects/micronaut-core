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
package io.micronaut.json.env;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link io.micronaut.context.env.PropertySourceLoader} that reads {@code application.json} files if they exist.
 *
 * <p>The JSON parser is provided by the default {@link JsonMapper}, allowing JSON configuration to work
 * with any supported Micronaut JSON implementation instead of requiring Jackson.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Experimental
public class JsonPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for property source loader.
     */
    public static final String FILE_EXTENSION = "json";

    private static final Argument<Map<String, Object>> MAP_ARGUMENT = Argument.mapOf(String.class, Object.class);

    private final JsonMapper jsonMapper;

    public JsonPropertySourceLoader() {
        this(JsonMapper.createDefault());
    }

    public JsonPropertySourceLoader(boolean logEnabled) {
        this(logEnabled, JsonMapper.createDefault());
    }

    JsonPropertySourceLoader(JsonMapper jsonMapper) {
        this(false, jsonMapper);
    }

    private JsonPropertySourceLoader(boolean logEnabled, JsonMapper jsonMapper) {
        super(logEnabled);
        this.jsonMapper = jsonMapper;
    }

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
     * @param input The input stream
     * @return map representation of the JSON
     * @throws IOException If the input stream cannot be read
     */
    protected Map<String, Object> readJsonAsMap(InputStream input) throws IOException {
        return Objects.requireNonNull(jsonMapper.readValue(input, MAP_ARGUMENT));
    }
}
