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

import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.core.annotation.Experimental;

/**
 * Receives the class changes of a development reload, registered with
 * {@link io.micronaut.context.WatchableBeanContext#watchClassChanges(ClassChangeWatcher)}: the watch for a
 * cache keyed by class, which forgets what it holds of a retired generation with
 * {@link ClassChangeEvent#isStale(Class)}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ClassChangeWatcher {

    /**
     * Called once per class change, before any bean of the retired generation is touched. There is no
     * startup batch: nothing has changed yet when the watch is registered.
     *
     * @param change The change
     */
    void onChange(ClassChangeEvent change);
}
