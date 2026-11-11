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
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;

import java.util.List;
import java.util.Objects;

/**
 * What a configuration refresh did.
 *
 * @param change The change applied
 * @param rebound The configuration beans injected again in place
 * @param recreated The configuration beans replaced by a new instance, bound through a constructor
 * @param disposed How many refreshable beans were disposed of, to be created again on use
 * @param outcomes What each configuration watch the change touched answered
 * @param kept The configuration beans the change did not reach, because replacing them, or destroying a removed
 *             {@code @EachProperty} entry, would have stopped the running application, as when a running server
 *             received them: the running instances are kept, a setter or field bean rebound in place, and the change
 *             reaches them fully on a restart, which {@link #requiresRestart()} reports. Only a context that records
 *             the dependency graph, as in development mode, keeps one
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public record RefreshResult(ConfigurationChange change,
                            List<BeanDefinition<?>> rebound,
                            List<BeanDefinition<?>> recreated,
                            int disposed,
                            List<ReloadingConfigurationWatcher.Outcome> outcomes,
                            List<BeanDefinition<?>> kept) {

    /**
     * Validating constructor.
     *
     * @param change The change
     * @param rebound The rebound beans
     * @param recreated The recreated beans
     * @param disposed The disposed count
     * @param outcomes The outcomes
     * @param kept The beans kept until a restart
     */
    public RefreshResult {
        Objects.requireNonNull(change, "change");
        rebound = List.copyOf(Objects.requireNonNull(rebound, "rebound"));
        recreated = List.copyOf(Objects.requireNonNull(recreated, "recreated"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        kept = List.copyOf(Objects.requireNonNull(kept, "kept"));
    }

    /**
     * A result that kept no bean.
     *
     * @param change The change
     * @param rebound The rebound beans
     * @param recreated The recreated beans
     * @param disposed The disposed count
     * @param outcomes The outcomes
     */
    public RefreshResult(ConfigurationChange change,
                         List<BeanDefinition<?>> rebound,
                         List<BeanDefinition<?>> recreated,
                         int disposed,
                         List<ReloadingConfigurationWatcher.Outcome> outcomes) {
        this(change, rebound, recreated, disposed, outcomes, List.of());
    }

    /**
     * @return Whether the change needs a restart of the application: a watch answered so, or a bean was
     * {@link #kept() kept} because replacing it would have stopped the running application
     */
    public boolean requiresRestart() {
        return !kept.isEmpty() || outcomes.contains(ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART);
    }
}
