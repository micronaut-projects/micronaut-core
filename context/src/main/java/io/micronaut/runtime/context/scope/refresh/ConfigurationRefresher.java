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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.Experimental;

/**
 * The one entry point of a configuration refresh, whoever asks for it: the {@code /refresh}
 * endpoint, a configuration watcher such as Consul's, a changed configuration file in development
 * mode, or a {@link RefreshEvent} something published.
 *
 * <p>A refresh runs in phases, in order: the {@code @ConfigurationReader} beans whose prefix the
 * change touches are rebound, in place for setter and field beans and by recreation for those bound
 * through a constructor; the {@code @Refreshable} beans the change affects are disposed of, after
 * the rebind so that none is recreated from stale values; each configuration watch whose prefix is
 * touched is told, and what it answers is collected; and a {@link ConfigurationRefreshedEvent}
 * carries the change and the {@link RefreshResult} to whoever listens.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface ConfigurationRefresher {

    /**
     * Reads the property sources again and applies what changed.
     *
     * @return The result
     */
    RefreshResult refresh();

    /**
     * Reads the property sources again and treats every property as changed.
     *
     * @return The result
     */
    RefreshResult refreshAll();

    /**
     * Applies a change the caller already computed, without reading the property sources again.
     *
     * @param change The change
     * @return The result
     */
    RefreshResult refresh(ConfigurationChange change);
}
