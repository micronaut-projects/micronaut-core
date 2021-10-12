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
package io.micronaut.http.filter;

/**
 * Represents phases of server filters. Each phase has a range of 1000 under its control.
 * There are gaps between phases to account for additions of future phases. Filters
 * relying on phases must ensure the order is within the selected phases range. For example
 * {@code ServerFilterPhase.TRACING.before() - 500} is considered an invalid usage of the TRACING
 * phase because that would place the order at 18251, which is outside of the range of the
 * phase (18501-19500).
 *
 * @author James Kleeh
 * @since 2.0.0
 */
public enum ServerFilterPhase {

    /**
     * The first phase, invoked before all others.
     */
    FIRST(-1000, -1249, -750),

    /**
     * Any filters related to collecting metrics.
     */
    METRICS(9000, 8751, 9250),

    /**
     * Any filters related to tracing HTTP calls.
     */
    TRACING(19000, 18751, 19250),

    /**
     * Any filters related to creating or reading the HTTP session.
     */
    SESSION(29000, 28751, 29250),

    /**
     * Any filters related to authentication or authorization.
     */
    SECURITY(39000, 38751, 39250),

    /**
     * Any filters related to rendering the response body.
     */
    RENDERING(49000, 48751, 49250),

    /**
     * The last phase, invoked after all other phases.
     */
    LAST(59000, 58751, 59250);

    private final int order;
    private final int before;
    private final int after;

    /**
     * @param order The order
     * @param before The order to run before
     * @param after The order to run after
     */
    ServerFilterPhase(int order, int before, int after) {
        this.order = order;
        this.before = before;
        this.after = after;
    }

    /**
     * @return The order of the phase
     */
    public int order() {
        return order;
    }

    /**
     * @return The order before the phase, but after any previous phases
     */
    public int before() {
        return before;
    }

    /**
     * @return The order after the phase, but before any future phases
     */
    public int after() {
        return after;
    }
}
