/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.discovery.vault.config.client.v2.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.Map;

/**
 *  Vault Data object.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Immutable
@Introspected
public class VaultResponseData {

    private Map<String, Object> data;
    private Map<String, Object> metadata;

    /**
     * Constructor for VaultResponseData.
     *
     * @param data The data map
     * @param metadata The metadata map
     */
    @JsonCreator
    @Internal
    public VaultResponseData(@JsonProperty("data") final Map<String, Object> data,
                             @JsonProperty("metadata") final Map<String, Object> metadata) {

        this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    /**
     * @return The data map
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Set the data map.
     *
     * @param data the data map
     */
    public void setData(final Map<String, Object> data) {
        this.data = data;
    }

    /**
     * @return The metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Set the metadata map.
     *
     * @param metadata the metadata map
     */
    public void setMetadata(final Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
