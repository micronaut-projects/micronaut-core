/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.particleframework.core.convert.ConversionService;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;


/**
 * Base class for all checks
 *
 * @see HTTPCheck
 * @author Graeme Rocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public abstract class Check {


    private Duration deregisterCriticalServiceAfter;
    private String name;
    private String ID;
    private HealthStatus status = HealthStatus.PASSING;
    private String notes;

    @JsonCreator
    protected Check(@JsonProperty("Name") String name) {
        this.name = name;
    }

    protected Check() {
    }


    /**
     * @return The name of the check
     */
    public String getName() {
        return name;
    }

    /**
     * @return A unique ID for the check
     */
    @JsonProperty("ID")
    public String getID() {
        return ID;
    }

    /**
     *
     * @return Human readable notes
     */
    public String getNotes() {
        return notes;
    }

    /**
     *
     * @return The health status
     */
    public String getStatus() {
        return status.toString().toLowerCase();
    }

    /**
     * @return The status an an enum
     */
    public HealthStatus status() {
        return status;
    }


    /**
     * @return The deregisterCriticalServiceAfter as a {@link Duration}
     */
    public Duration deregisterCriticalServiceAfter() {
        return this.deregisterCriticalServiceAfter;
    }

    public Optional<String> getDeregisterCriticalServiceAfter() {
        if(deregisterCriticalServiceAfter == null) {
            return Optional.empty();
        }
        return Optional.of(deregisterCriticalServiceAfter.toMinutes() + "m");
    }


    void setName(String name) {
        this.name = name;
    }

    void setDeregisterCriticalServiceAfter(String deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = ConversionService.SHARED.convert(deregisterCriticalServiceAfter, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid deregisterCriticalServiceAfter Specified"));
    }


    @JsonProperty("ID")
    void setID(String ID) {
        this.ID = ID;
    }

    void setStatus(String status) {
        this.status = HealthStatus.valueOf(status.toUpperCase());
    }

    void setNotes(String notes) {
        this.notes = notes;
    }


    public Check deregisterCriticalServiceAfter(Duration interval) {
        if(interval != null) {
            this.deregisterCriticalServiceAfter = interval;
        }
        return this;
    }

    public Check deregisterCriticalServiceAfter(String interval) {
        this.deregisterCriticalServiceAfter = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid deregisterCriticalServiceAfter Specified"));
        return this;
    }
    public Check id(String ID) {
        setID(ID);
        return this;
    }

    public Check status(HealthStatus status) {
        this.status = status;
        return this;
    }

    public Check notes(String notes) {
        this.notes = notes;
        return this;
    }

    /**
     * Valid health status values
     */
    @JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
    public enum HealthStatus {
        PASSING, WARNING, CRITICAL
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Check check = (Check) o;
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
}
