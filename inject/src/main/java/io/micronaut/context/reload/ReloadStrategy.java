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
package io.micronaut.context.reload;

import io.micronaut.core.annotation.Experimental;

/**
 * How a development launcher applies a change of the application's classes.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum ReloadStrategy {
    /**
     * The application context is stopped and a new one started on the new classes.
     */
    RESTART,
    /**
     * The application context stays: the changed classes are redefined in place, and the running beans see
     * the new method bodies.
     */
    RELOAD,
    /**
     * {@link #RELOAD} when the instrumentation agent is attached and the change allows it, {@link #RESTART}
     * otherwise. A launcher setting only: a published change carries the strategy it applied.
     */
    AUTO
}
