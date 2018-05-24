/*
 * Copyright 2017-2018 original authors
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.util.StringUtils;

import java.util.Locale;

/**
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class CheckEntry implements Check {

    private final String id;

    private String notes;
    private String name;
    private String status;

    /**
     * @param id The id
     */
    @JsonCreator
    protected CheckEntry(@JsonProperty("CheckID") String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @param name The name of the check
     */
    protected void setName(String name) {
        this.name = name;
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    /**
     * @param notes The human readable notes
     */
    protected void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * @return The status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status The status
     */
    protected void setStatus(String status) {
        this.status = status;
    }

    @Override
    public Status status() {
        if (StringUtils.isNotEmpty(status)) {
            return Status.valueOf(status.toUpperCase(Locale.ENGLISH));
        }
        return Status.PASSING;
    }
}

