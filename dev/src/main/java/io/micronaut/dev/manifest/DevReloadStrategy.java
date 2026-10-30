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
package io.micronaut.dev.manifest;

import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.annotation.Experimental;

/**
 * The {@code micronaut.dev.strategy} a manifest asks the development launcher for. The launcher decides per change
 * which {@link ReloadStrategy} it applies, and the published {@link io.micronaut.context.reload.ClassChangeEvent}
 * carries that one.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum DevReloadStrategy {
    /**
     * Every change restarts: {@link ReloadStrategy#RESTART}.
     */
    RESTART,
    /**
     * A change to method bodies alone is applied in place, {@link ReloadStrategy#RELOAD}, when the instrumentation
     * agent is attached; anything else restarts.
     */
    RELOAD,
    /**
     * The default: as {@link #RELOAD} when the instrumentation agent is attached, as {@link #RESTART} otherwise.
     */
    AUTO
}
