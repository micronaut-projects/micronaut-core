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

import io.micronaut.core.annotation.NonNull;

import java.util.Map;

/**
 * Base JsonNode class for scalar values (null, number, string, boolean).
 *
 * @author Jonas Konrad
 * @since 3.1
 */
abstract class JsonScalar extends JsonNode {
    @Override
    public int size() {
        return 0;
    }

    @Override
    @NonNull
    public Iterable<JsonNode> values() {
        throw new IllegalStateException("Not a container");
    }

    @Override
    @NonNull
    public Iterable<Map.Entry<String, JsonNode>> entries() {
        throw new IllegalStateException("Not an object");
    }

    @Override
    public boolean isValueNode() {
        return true;
    }

    @Override
    public JsonNode get(@NonNull String fieldName) {
        return null;
    }

    @Override
    public JsonNode get(int index) {
        return null;
    }
}
