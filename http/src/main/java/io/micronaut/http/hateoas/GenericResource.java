/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.hateoas;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ObjectUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Resource} with indeterminate structure. This is used as the deserialization target of
 * {@link Resource#getEmbedded()}, because we can't determine the type of the resource for deserialization, there.
 *
 * @since 3.4.0
 * @author yawkat
 */
@Introspected
public final class GenericResource extends AbstractResource<GenericResource> {
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    /**
     * Create a new resource. Note: Should only be called by deserialization – if you wish to create your own
     * {@link Resource}, please create a custom implementation of {@link AbstractResource}.
     */
    @Internal
    public GenericResource() {
    }

    /**
     * Add a property to this resource (internal, for deserialization use only).
     *
     * @param key Property key
     * @param v Property value
     */
    @Internal
    @JsonAnySetter
    public void addProperty(String key, Object v) {
        additionalProperties.put(key, v);
    }

    /**
     * Get the properties of this resource, as an untyped map.
     *
     * @return The properties of this resource
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenericResource &&
                getLinks().equals(((GenericResource) o).getLinks()) &&
                getEmbedded().equals(((GenericResource) o).getEmbedded()) &&
                getAdditionalProperties().equals(((GenericResource) o).getAdditionalProperties());
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(getLinks(), getEmbedded(), getAdditionalProperties());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("GenericResource{").append("_links=").append(getLinks()).append(", _embedded=").append(getEmbedded());
        additionalProperties.forEach((k, v) -> sb.append(", ").append(k).append('=').append(v));
        return sb.append('}').toString();
    }
}
