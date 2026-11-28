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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;

/**
 * Internal access to lifecycle coordination for environment implementations outside this package.
 *
 * @author Nimesh Sharma
 * @since 4.10.38
 */
@Internal
public final class DefaultEnvironmentAccess {

    private DefaultEnvironmentAccess() {
    }

    /**
     * Run an environment start with the coordination used by {@link DefaultEnvironment#start()}.
     *
     * @param environment The environment
     * @param start The start operation
     */
    public static void start(DefaultEnvironment environment, Runnable start) {
        environment.coordinateStart(start);
    }

    /**
     * Run an environment stop with the coordination used by {@link DefaultEnvironment#stop()}.
     *
     * @param environment The environment
     * @param stop The stop operation
     */
    public static void stop(DefaultEnvironment environment, Runnable stop) {
        environment.coordinateStop(stop);
    }
}
