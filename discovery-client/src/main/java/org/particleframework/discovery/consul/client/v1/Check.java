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
import java.time.temporal.ChronoUnit;
import java.util.Optional;


/**
 * Base class for all checks
 *
 * @see HttpCheck
 * @author Graeme Rocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public abstract class Check {

    private Duration interval = Duration.of(10, ChronoUnit.SECONDS);
    private Duration deregisterCriticalServiceAfter;
    private final String name;
    private String ID;
    private HealthStatus status = HealthStatus.PASSING;
    private String notes;

    @JsonCreator
    protected Check(@JsonProperty("Name") String name) {
        this.name = name;
    }

    public String getInterval() {
        return interval.getSeconds() + "s";
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
     * @return The interval as a {@link Duration}
     */
    public Duration interval() {
        return this.interval;
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

    public void setDeregisterCriticalServiceAfter(String deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = ConversionService.SHARED.convert(deregisterCriticalServiceAfter, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid deregisterCriticalServiceAfter Specified"));
    }

    public void setInterval(String interval) {
        this.interval = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid Duration Specified"));
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public void setStatus(String status) {
        this.status = HealthStatus.valueOf(status.toUpperCase());
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Check interval(Duration interval) {
        if(interval != null) {
            this.interval = interval;
        }
        return this;
    }

    public Check interval(String interval) {
        this.interval = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid Duration Specified"));
        return this;
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
    enum HealthStatus {
        PASSING, WARNING, CRITICAL
    }
}
