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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;

/**
 * Receives the configuration changes under a prefix, registered with
 * {@link io.micronaut.context.BeanContext#watchConfiguration(String, ConfigurationWatcher)}, and says what
 * it did about them.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ConfigurationWatcher {

    /**
     * Called after the configuration beans under the prefix have been rebound, so that the watcher reads
     * the new values from them. A watch registered with a first batch is also called once when it is
     * registered, with a change whose {@link ConfigurationChange#initial()} holds, to read the values as
     * they are; its answer to that call is not acted on.
     *
     * @param change The change, which touches the watched prefix
     * @return What the watcher did
     */
    Outcome onChange(ConfigurationChange change);

    /**
     * What a watcher did with a change.
     */
    enum Outcome {
        /**
         * The change was applied to the live bean.
         */
        APPLIED,
        /**
         * The bean that registered the watch while it was created must be replaced; its dependents follow
         * through the dependency graph. Only a watch registered during the creation of a bean the context
         * holds, a singleton or a scoped bean, can answer this: for any other watch the outcome is reported
         * as {@link #IGNORED}.
         */
        RECREATE,
        /**
         * The change cannot be applied without restarting the application.
         */
        REQUIRES_RESTART,
        /**
         * The change does not concern the watcher after all.
         */
        IGNORED
    }
}
