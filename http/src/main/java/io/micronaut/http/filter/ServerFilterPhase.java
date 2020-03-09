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
package io.micronaut.http.filter;

/**
 * Represents phases of server filters. Each phase has a range of 1000 under its control.
 * There are gaps between phases to account for additions of future phases. Filters
 * relying on phases must ensure the order is within the selected phases range. For example
 * {@code ServerFilterPhase.TRACING.before() - 500} is considered an invalid usage of the TRACING
 * phase because that would place the order at 2251, which is outside of the range of the
 * phase (2501-3500).
 *
 * @author James Kleeh
 * @since 2.0.0
 */
public enum ServerFilterPhase {

    FIRST(-1000, -1249, -750),
    METRICS(1000, 751, 1250),
    TRACING(3000, 2751, 3250),
    SESSION(5000, 4751, 5250),
    SECURITY(7000, 6751, 7250),
    VIEWS(9000, 8751, 9250),
    LAST(11000, 10751, 11250);

    private final Integer order;
    private final Integer before;
    private final Integer after;

    /**
     * @param order The order
     * @param before The order to run before
     * @param after The order to run after
     */
    ServerFilterPhase(Integer order, Integer before, Integer after) {
        this.order = order;
        this.before = before;
        this.after = after;
    }

    /**
     * @return The order of the phase
     */
    public Integer order() {
        return order;
    }

    /**
     * @return The order before the phase, but after any previous phases
     */
    public Integer before() {
        return before;
    }

    /**
     * @return The order after the phase, but before any future phases
     */
    public Integer after() {
        return after;
    }
}
