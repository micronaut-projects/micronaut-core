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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Internal;

/**
 * Static holder for the {@link Mode} currently used to control {@link PropagatedContext}
 * behaviour.
 */
@Internal
public final class PropagatedContextConfiguration {

    private static volatile Mode current = Mode.SCOPED_VALUE;

    private PropagatedContextConfiguration() {
    }

    /**
     * @return The current propagation mode
     */
    public static Mode get() {
        return current;
    }

    /**
     * Sets the current propagation mode.
     * @param mode The mode to apply
     */
    public static void set(Mode mode) {
        current = java.util.Objects.requireNonNull(mode, "mode");
    }

    /**
     * Reset to the default {@link Mode#SCOPED_VALUE}.
     */
    public static void reset() {
        current = Mode.SCOPED_VALUE;
    }

    /**
     * The available propagation modes.
     */
    public enum Mode {
        /**
         * Enable scoped values propagation.
         */
        SCOPED_VALUE,
        /**
         * Enable thread-local propagation.
         */
        THREAD_LOCAL
    }
}
