/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.naming.Named;
import io.micronaut.http.MediaType;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for message body handlers to declare additional media types.
 *
 * @author Micronaut Framework
 * @since 5.0
 */
@EachProperty(MessageBodyConfiguration.PREFIX)
public class MessageBodyConfiguration implements Named {

    /**
     * Configuration prefix, retained for backwards compatibility.
     */
    public static final String PREFIX = "micronaut.codec";

    private List<MediaType> additionalTypes = Collections.emptyList();
    private final String name;

    public MessageBodyConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return Media types in addition to the default that the handler should process.
     */
    public List<MediaType> getAdditionalTypes() {
        return additionalTypes;
    }

    /**
     * Default value (Empty list).
     *
     * @param additionalTypes additional types
     */
    public void setAdditionalTypes(List<MediaType> additionalTypes) {
        this.additionalTypes = additionalTypes;
    }

    @Override
    public String getName() {
        return name;
    }
}
