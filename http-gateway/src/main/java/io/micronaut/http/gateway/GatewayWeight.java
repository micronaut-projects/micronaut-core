/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * The weight of a route in a group of routes that split the traffic, like Spring Cloud
 * Gateway's {@code Weight} predicate, e.g. a canary: of the routes of a group that match a
 * request, one is chosen by weight; the others do not match it. A route of weight {@code 0} is
 * only chosen through the override header of the group, see
 * {@link GatewayWeightGroupConfiguration}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayWeight {

    private final String group;
    private final int weight;

    private GatewayWeight(String group, int weight) {
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) {
            throw new IllegalArgumentException("The group of a weight is blank");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("A weight is at least 0: " + weight);
        }
        this.group = group;
        this.weight = weight;
    }

    /**
     * @param group  The name of the group
     * @param weight The weight of the route in the group, at least 0
     * @return The weight
     */
    public static GatewayWeight of(String group, int weight) {
        return new GatewayWeight(group, weight);
    }

    /**
     * @return The name of the group
     */
    public String group() {
        return group;
    }

    /**
     * @return The weight of the route in the group, at least 0
     */
    public int weight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GatewayWeight that && weight == that.weight && group.equals(that.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, weight);
    }

    @Override
    public String toString() {
        return "Weight=" + group + ", " + weight;
    }
}
