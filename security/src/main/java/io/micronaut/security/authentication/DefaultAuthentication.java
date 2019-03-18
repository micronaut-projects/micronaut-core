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
package io.micronaut.security.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

import javax.annotation.concurrent.Immutable;
import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of the Authentication interface.
 *
 * @author James Kleeh
 * @since 1.0.1
 */
@Immutable
@Introspected
public class DefaultAuthentication implements Authentication {

    private final String name;
    private final Map<String, Object> attributes;

    /**
     *
     * @param name The name of the authentication
     * @param attributes The attributes for the authentication
     */
    @JsonCreator
    public DefaultAuthentication(@JsonProperty("name") String name,
                                 @JsonProperty("attributes") Map<String, Object> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
}
