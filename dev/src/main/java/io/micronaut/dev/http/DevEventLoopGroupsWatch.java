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
package io.micronaut.dev.http;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;

/**
 * Restarts the application for a change of the configuration the {@link DevEventLoopGroups retained event loop
 * groups} were made from: the groups cannot be resized or given other threads in place, and the restart releases them
 * so that the next generation makes them again. Made by each generation, since the groups outlive the one that made
 * this watch, and tells them when it stops.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@Requires(beans = DevEventLoopGroups.class)
final class DevEventLoopGroupsWatch {

    private final List<BeanWatch> watches = new ArrayList<>(2);
    private final DevEventLoopGroups groups;

    DevEventLoopGroupsWatch(BeanContext context, DevEventLoopGroups groups) {
        this.groups = groups;
        if (!(context instanceof WatchableBeanContext watchable)) {
            return;
        }
        ReloadingConfigurationWatcher watcher = change -> {
            if (change.initial() || change.all() || groups.isEmpty()) {
                // a refresh of everything names no key, and restarts nothing on its own
                return ReloadingConfigurationWatcher.Outcome.IGNORED;
            }
            return ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART;
        };
        watches.add(watchable.configuration(EventLoopGroupConfiguration.EVENT_LOOPS).watchReloading(watcher));
        watches.add(watchable.configuration(NettyGlobalConfiguration.PREFIX).watchReloading(watcher));
    }

    /**
     * The generation stops: the groups it did not ask for are shut down.
     */
    @PreDestroy
    void close() {
        watches.forEach(BeanWatch::close);
        groups.generationEnded();
    }
}
