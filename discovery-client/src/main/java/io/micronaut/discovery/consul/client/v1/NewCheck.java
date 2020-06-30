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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.convert.ConversionService;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for all checks.
 *
 * @author Graeme Rocher
 * @see HTTPCheck
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@Introspected
public abstract class NewCheck implements Check {

    private Duration deregisterCriticalServiceAfter;
    private String name;
    private String ID;
    private Status status = Status.PASSING;
    private String notes;

    /**
     * @param name The name
     */
    @JsonCreator
    protected NewCheck(@JsonProperty("Name") String name) {
        this.name = name;
    }

    /**
     * Default constructor.
     */
    protected NewCheck() {
    }

    /**
     * @return The name of the check
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return A unique ID for the check
     */
    @Override
    @JsonProperty("ID")
    public String getID() {
        return ID;
    }

    /**
     * @return Human readable notes
     */
    @Override
    public String getNotes() {
        return notes;
    }

    /**
     * @return The health status
     */
    public String getStatus() {
        return status.toString().toLowerCase();
    }

    /**
     * @return The status an an enum
     */
    @Override
    public Status status() {
        return status;
    }

    /**
     * @return The deregisterCriticalServiceAfter as a {@link Duration}
     */
    public Duration deregisterCriticalServiceAfter() {
        return this.deregisterCriticalServiceAfter;
    }

    /**
     * @return The deregisterCriticalServiceAfter as a {@link Optional}
     */
    public Optional<String> getDeregisterCriticalServiceAfter() {
        if (deregisterCriticalServiceAfter == null) {
            return Optional.empty();
        }
        return Optional.of(deregisterCriticalServiceAfter.toMinutes() + "m");
    }

    /**
     * @param interval The deregisterCriticalServiceAfter as a {@link Duration}
     * @return The {@link NewCheck} instance
     */
    public NewCheck deregisterCriticalServiceAfter(Duration interval) {
        if (interval != null) {
            this.deregisterCriticalServiceAfter = interval;
        }
        return this;
    }

    /**
     * @param interval The deregisterCriticalServiceAfter as a string
     * @return The {@link NewCheck} instance
     */
    public NewCheck deregisterCriticalServiceAfter(String interval) {
        this.deregisterCriticalServiceAfter = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(() -> new IllegalArgumentException("Invalid deregisterCriticalServiceAfter Specified"));
        return this;
    }

    /**
     * @param ID The ID of the check
     * @return The deregisterCriticalServiceAfter as a {@link Duration}
     */
    public NewCheck id(@SuppressWarnings("ParameterName") String ID) {
        setID(ID);
        return this;
    }

    /**
     * @param status The {@link io.micronaut.discovery.consul.client.v1.Check.Status} of the check
     * @return The deregisterCriticalServiceAfter as a {@link Duration}
     */
    public NewCheck status(Status status) {
        this.status = status;
        return this;
    }

    /**
     * @param notes The human readable notes
     * @return The deregisterCriticalServiceAfter as a {@link Duration}
     */
    public NewCheck notes(String notes) {
        this.notes = notes;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NewCheck check = (NewCheck) o;
        return Objects.equals(deregisterCriticalServiceAfter, check.deregisterCriticalServiceAfter) &&
            Objects.equals(name, check.name) &&
            Objects.equals(ID, check.ID) &&
            status == check.status &&
            Objects.equals(notes, check.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deregisterCriticalServiceAfter, name, ID, status, notes);
    }

    /**
     * @param name The name
     */
    @ReflectiveAccess
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * @param deregisterCriticalServiceAfter Service to de-regsiter after
     */
    protected void setDeregisterCriticalServiceAfter(String deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = ConversionService.SHARED.convert(deregisterCriticalServiceAfter, Duration.class).orElseThrow(() -> new IllegalArgumentException("Invalid deregisterCriticalServiceAfter Specified"));
    }

    /**
     * @param ID The id
     */
    @JsonProperty("ID")
    @ReflectiveAccess
    void setID(@SuppressWarnings("ParameterName") String ID) {
        this.ID = ID;
    }

    /**
     * @param status The status
     */
    @ReflectiveAccess
    protected void setStatus(String status) {
        this.status = Status.valueOf(status.toUpperCase());
    }

    /**
     * @param notes The notes
     */
    @ReflectiveAccess
    protected void setNotes(String notes) {
        this.notes = notes;
    }
}
