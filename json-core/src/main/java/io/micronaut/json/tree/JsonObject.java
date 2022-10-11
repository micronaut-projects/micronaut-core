/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.tree;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.Map;

/**
 * Public to allow special handling for conversion service. Use {@link JsonNode#isObject()} to distinguish nodes.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Internal
public class JsonObject extends JsonContainer {
    private final Map<String, JsonNode> values;

    JsonObject(Map<String, JsonNode> values) {
        this.values = values;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonNode get(@NonNull String fieldName) {
        return values.get(fieldName);
    }

    @Override
    public JsonNode get(int index) {
        return null;
    }

    @Override
    @NonNull
    public Iterable<JsonNode> values() {
        return values.values();
    }

    @Override
    @NonNull
    public Iterable<Map.Entry<String, JsonNode>> entries() {
        return values.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonObject && ((JsonObject) o).values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
