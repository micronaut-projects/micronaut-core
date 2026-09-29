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
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;

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
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record RefreshResult(ConfigurationChange change,
                            List<BeanDefinition<?>> rebound,
                            List<BeanDefinition<?>> recreated,
                            int disposed,
                            List<ConfigurationWatcher.Outcome> outcomes) {

    /**
     * Validating constructor.
     *
     * @param change The change
     * @param rebound The rebound beans
     * @param recreated The recreated beans
     * @param disposed The disposed count
     * @param outcomes The outcomes
     */
    public RefreshResult {
        Objects.requireNonNull(change, "change");
        rebound = List.copyOf(Objects.requireNonNull(rebound, "rebound"));
        recreated = List.copyOf(Objects.requireNonNull(recreated, "recreated"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    }

    /**
     * @return Whether a watch answered that the change needs a restart of the application
     */
    public boolean requiresRestart() {
        return outcomes.contains(ConfigurationWatcher.Outcome.REQUIRES_RESTART);
    }
}
