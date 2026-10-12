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
package io.micronaut.context.env;

import io.micronaut.context.LifeCycle;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * The {@link LifeCycle#refresh()} of {@link DefaultEnvironment}, which publishes the properties
 * read by the refresh in one step.
 *
 * <p>The override lives here, declared against the type variable, and not in
 * {@link DefaultEnvironment} to stay binary compatible. Declared there it would return
 * {@link Environment}, and the compiler would add a bridge method with the erased descriptor of
 * {@link LifeCycle#refresh()}. A subclass compiled against an earlier version that overrides
 * {@code refresh()} and calls {@code super.refresh()} is linked against that erased descriptor: it
 * would reach the bridge, which dispatches back to the subclass, and recurse until the stack
 * overflows. Declared against the type variable the override has the erased descriptor itself and
 * there is no bridge.</p>
 *
 * <p>The interface is public only so that {@code refresh()} stays invocable through reflection on
 * the environment class; it is not meant to be implemented by anything else.</p>
 *
 * @param <T> The life cycle type
 * @since 4.10.32
 */
@Internal
public interface AtomicRefresh<T extends LifeCycle<T>> extends LifeCycle<T> {

    @Override
    @SuppressWarnings("unchecked")
    default @NonNull T refresh() {
        // Rebuild into a replacement catalog and publish it in one step, so a concurrent lookup
        // sees the properties either as they were before the refresh or as they are after it.
        ((PropertySourcePropertyResolver) this).refresh(() -> {
            stop();
            start();
        });
        return (T) this;
    }
}
