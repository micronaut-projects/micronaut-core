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
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a module keeps in a static of the launcher's tier across the application contexts of a development launcher,
 * such as a warm engine, and releases when the launcher closes. A module that the launcher does not know registers
 * the release here, and the launcher runs the releases when it closes, so that what the module kept, and the
 * generation it holds, does not outlive the launcher in a process that goes on.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Experimental
public final class LauncherCloseActions {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherCloseActions.class);
    private static final Object LOCK = new Object();
    // guarded by LOCK
    private static final Map<String, Runnable> ACTIONS = new LinkedHashMap<>();

    private LauncherCloseActions() {
    }

    /**
     * Registers what to release when the launcher closes, once per key: a later registration under the same key
     * replaces the earlier one.
     *
     * @param key Identifies the release
     * @param action The release
     */
    public static void register(String key, Runnable action) {
        synchronized (LOCK) {
            ACTIONS.put(key, action);
        }
    }

    /**
     * Runs and forgets every registered release: the launcher closes. A release that fails does not keep the next one
     * from running.
     */
    public static void runAll() {
        List<Map.Entry<String, Runnable>> actions;
        synchronized (LOCK) {
            actions = new ArrayList<>(ACTIONS.entrySet());
            ACTIONS.clear();
        }
        for (Map.Entry<String, Runnable> action : actions) {
            try {
                action.getValue().run();
            } catch (RuntimeException e) {
                LOG.warn("Releasing {} as the development launcher closes failed: {}", action.getKey(), e.getMessage(), e);
            }
        }
    }
}
