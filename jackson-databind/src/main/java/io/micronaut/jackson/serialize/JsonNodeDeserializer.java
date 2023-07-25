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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Deserializer for {@link JsonNode}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Singleton
public final class JsonNodeDeserializer extends JsonDeserializer<JsonNode> {
    @Override
    public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return JsonNodeTreeCodec.getInstance().readTree(p);
    }
}
