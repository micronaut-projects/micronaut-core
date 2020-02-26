/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * @author graemerocher
 * @since 1.0
 */
public interface Check {

    /**
     * @return The name of the check
     */
    String getName();

    /**
     * @return A unique ID for the check
     */
    String getID();

    /**
     * @return Human readable notes
     */
    String getNotes();

    /**
     * @return The status an an enum
     */
    Status status();

    /**
     * Valid health status values.
     */
    @JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
    enum Status {
        PASSING, WARNING, CRITICAL
    }
}
