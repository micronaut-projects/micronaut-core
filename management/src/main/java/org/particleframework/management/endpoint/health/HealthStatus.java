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
package org.particleframework.management.endpoint.health;

import javax.annotation.concurrent.Immutable;
import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * <p>The status of a health indicator</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Immutable
public class HealthStatus implements Comparable<HealthStatus> {

    private final String name;
    private final Optional<String> description;
    private final Optional<Boolean> operational;
    private final Optional<Integer> severity;

    public static final HealthStatus UP = new HealthStatus("UP", null, true, null);
    public static final HealthStatus DOWN = new HealthStatus("DOWN", null, false, 1000);
    public static final HealthStatus UNKNOWN = new HealthStatus("UNKNOWN");

    public HealthStatus(@NotNull String name, String description, Boolean operational, Integer severity) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null when creating a health status");
        }
        this.name = name;
        this.description = Optional.ofNullable(description);
        this.operational = Optional.ofNullable(operational);
        this.severity = Optional.ofNullable(severity);
    }

    public HealthStatus(@NotNull String name) {
        this(name, null, null, null);
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
        return description;
    }

    /**
     * Whether the status represents a functioning service
     *
     * @return Empty if partially operational.
     */
    public Optional<Boolean> getOperational() {
        return operational;
    }

    /**
     * The severity of the status. A higher severity indicates
     * a more severe error.
     *
     * @return Empty if no severity specified.
     */
    public Optional<Integer> getSeverity() {
        return severity;
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
        if (operational.isPresent() && o.operational.isPresent()) {
            return operational.get().compareTo(o.operational.get()) * -1;
        } else if (operational.isPresent()) {
            return operational.get() == Boolean.TRUE ? -1 : 1;
        } else if (o.operational.isPresent()) {
            return o.operational.get() == Boolean.TRUE ? 1 : -1;
        } else {
            if (severity.isPresent() && o.severity.isPresent()) {
                return severity.get().compareTo(o.severity.get());
            } else if (severity.isPresent()) {
                return 1;
            } else if (o.severity.isPresent()) {
                return -1;
            } else {
                return 0;
            }
        }
    }

}
