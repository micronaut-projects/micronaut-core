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
 * Receives the configuration changes a {@link ConfigurationWatchRequest} selects, after the configuration beans they
 * concern were bound again, and applies them to what the watcher derived from the configuration.
 *
 * <p>A watcher that may not be able to apply a change, and needs its bean recreated or the application restarted,
 * is a {@link ReloadingConfigurationWatcher} instead.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ConfigurationWatcher {

    /**
     * Called after the configuration beans under the watched prefix have been bound again, so that the watcher
     * reads the new values from them. A watch registered {@link ConfigurationWatchRequest#withFirstBatch() with a
     * first batch} is also called once when it is registered, with a change whose
     * {@link ConfigurationChange#initial()} holds, to read the values as they are.
     *
     * @param change The change, which touches the watched prefix
     */
    void onChange(ConfigurationChange change);
}
