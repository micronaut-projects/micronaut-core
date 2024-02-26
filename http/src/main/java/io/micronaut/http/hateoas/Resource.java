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
package io.micronaut.http.hateoas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Represents a REST resource in a hateoas architecture.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@Serdeable
@Introspected
public interface Resource {

    /**
     * The links attribute.
     */
    String LINKS = "_links";

    /**
     * The embedded attribute.
     */
    String EMBEDDED = "_embedded";

    /**
     * @return The links for this resource
     */
    @JsonProperty(LINKS)
    default OptionalMultiValues<? extends Link> getLinks() {
        return OptionalMultiValues.empty();
    }

    /**
     * @return The embedded resources
     */
    @JsonProperty(EMBEDDED)
    default OptionalMultiValues<? extends Resource> getEmbedded() {
        return OptionalMultiValues.empty();
    }

    /**
     * Factory method for deserialization.
     *
     * @param genericResource The deserialized resource.
     * @return The deserialized resource.
     */
    @Internal
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static Resource deserialize(GenericResource genericResource) {
        return genericResource;
    }
}
