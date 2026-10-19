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
 * Receives the configuration changes a {@link ConfigurationWatchRequest} selects, registered with
 * {@link ConfigurationWatchRequest#watchReloading(ReloadingConfigurationWatcher)}, and says what it did about each:
 * the form for a watcher that cannot always apply a change in place, such as one whose bean must be created again,
 * or a server whose port changed, which a development launcher restarts. The {@link Outcome outcomes} are returned
 * to the configuration refresh, which acts on them.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ReloadingConfigurationWatcher {

    /**
     * Called as {@link ConfigurationWatcher#onChange(ConfigurationChange)} is. The answer to the first batch, if the
     * watch asked for one, is not acted on: the bean that registered the watch, if it has one, is still being created.
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
         * The change was applied to the live bean. A {@link ConfigurationWatcher} always answers this.
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
