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
import io.micronaut.core.order.Ordered;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Set;

/**
 * Applies a change of the application's resources to the running application in place, so that a
 * development launcher need not {@link ReloadStrategy#RESTART restart} it.
 *
 * <p>A launcher that found resources changed in its reloadable roots, and no class, asks the
 * reloaders of the running context, in {@link Ordered order}, before it restarts: the first that
 * {@link #canReload(Set, Set) can} take the whole change {@link #reload(Set) applies} it. The
 * launcher makes the new contents readable through the context's class loader first, so that a
 * reloader reads them as it would after a restart. A reloader that throws refuses the change, and
 * the launcher restarts instead: the new generation it builds discards whatever the reloader had
 * applied before it failed, which is why a reloader need not undo a partial change.</p>
 *
 * <p>Implementations are beans that exist only while development mode is active
 * ({@code @Requires(condition = DevelopmentMode.Active.class)}). The Python runtime's reloader
 * patches the changed modules into every running interpreter, keeping their module, class and
 * function objects, which the generated classes and the caches built on them still refer to.</p>
 *
 * <p>Resources are named by their path relative to the root of the class path that holds them,
 * with {@code /} as separator, as {@link ClassLoader#getResource(String)} names them.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface InPlaceResourceReloader extends Ordered {

    /**
     * Whether this reloader can apply the whole change in place. Called before the new contents are
     * readable, so the answer depends on the names alone. A reloader answers {@code false} for a change
     * that holds a resource it does not handle, or one it cannot apply, such as one that adds or removes
     * a resource.
     *
     * @param changedResources The resources whose contents changed
     * @param removedResources The resources removed
     * @return True if {@link #reload(Set)} would apply every one of them
     */
    boolean canReload(Set<String> changedResources, Set<String> removedResources);

    /**
     * Applies the change to the running application. The new contents are readable through the
     * application context's class loader when this is called.
     *
     * @param changedResources The resources whose contents changed
     * @return What was reloaded, for the launcher to report
     * @throws Exception to refuse the change, after which the launcher restarts the application
     */
    Result reload(Set<String> changedResources) throws Exception;

    /**
     * What a reload applied.
     *
     * @param count How many units were reloaded
     * @param unit What a unit is, in the plural form the launcher reports it with, such as {@code "Python module(s)"}
     */
    record Result(int count, String unit) {

        /**
         * Validating constructor.
         *
         * @param count The count
         * @param unit The unit
         */
        public Result {
            Objects.requireNonNull(unit, "unit");
        }
    }
}
