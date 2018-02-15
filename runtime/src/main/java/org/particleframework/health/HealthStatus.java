/*
 * Copyright 2017 original authors
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
package org.particleframework.health;

import javax.annotation.concurrent.Immutable;
import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * <p>The status of a health indicator</p>
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Immutable
public class HealthStatus implements Comparable<HealthStatus> {

    private final String name;
    private final String description;
    private final Boolean operational;
    private final Integer severity;

    /**
     * The default name to use for an {@link #UP} status
     */
    public static final String NAME_UP = "UP";
    /**
     * The default name to use for an {@link #DOWN} status
     */
    public static final String NAME_DOWN = "DOWN";
    /**
     * Indicates the service is operational
     */
    public static final HealthStatus UP = new HealthStatus(NAME_UP, null, true, null);
    /**
     * Indicates the service is down and unavailable
     */
    public static final HealthStatus DOWN = new HealthStatus(NAME_DOWN, null, false, 1000);

    public static final HealthStatus UNKNOWN = new HealthStatus("UNKNOWN");

    public HealthStatus(@NotNull String name, String description, Boolean operational, Integer severity) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null when creating a health status");
        }
        this.name = name;
        this.description = description;
        this.operational = operational;
        this.severity = severity;
    }

    public HealthStatus(@NotNull String name) {
        this(name, null, null, null);
    }


    /**
     * Describe an existing {@link HealthStatus}
     * @param description The description
     * @return The new health status
     */
    public HealthStatus describe(String description) {
        return new HealthStatus(name, description, operational, severity);
    }

    /**
     * @return The name of the status
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of the status
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * Whether the status represents a functioning service
     *
     * @return Empty if partially operational.
     */
    public Optional<Boolean> getOperational() {
        return Optional.ofNullable(operational);
    }

    /**
     * The severity of the status. A higher severity indicates
     * a more severe error.
     *
     * @return Empty if no severity specified.
     */
    public Optional<Integer> getSeverity() {
        return Optional.ofNullable(severity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HealthStatus that = (HealthStatus) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Sorts statuses in order of "functioning level". The most functional
     * will appear first and the least functional will appear last.
     *
     * Operation is sorted (true, null, false). For statuses with matching
     * operations, severity is sorted ascending, with nulls first.
     *
     * @param o The status to compare
     * @return -1, 1, or 0
     */
    @Override
    public int compareTo(HealthStatus o) {
        if (operational != null && o.operational != null) {
            return operational.compareTo(o.operational) * -1;
        } else if (operational != null) {
            return operational == Boolean.TRUE ? -1 : 1;
        } else if (o.operational != null) {
            return o.operational == Boolean.TRUE ? 1 : -1;
        } else {
            if (severity != null && o.severity != null) {
                return severity.compareTo(o.severity);
            } else if (severity != null) {
                return 1;
            } else if (o.severity != null) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
                "name='" + name + '\'' +
                ", description=" + description +
                ", operational=" + operational +
                ", severity=" + severity +
                '}';
    }
}
