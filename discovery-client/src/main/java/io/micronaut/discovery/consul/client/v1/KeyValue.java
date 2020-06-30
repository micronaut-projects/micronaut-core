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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents a Key/Value pair returned from Consul via /kv/:key.
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@Introspected
public class KeyValue {
    private String key;
    private String value;

    /**
     * @param key   The key
     * @param value The value
     */
    @JsonCreator
    public KeyValue(@JsonProperty("Key") String key, @JsonProperty("Value") String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * @return The key
     */
    public String getKey() {
        return key;
    }

    /**
     * @return The value
     */
    public String getValue() {
        return value;
    }
}
