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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.UnmatchedRequestArgumentBinder;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * How the arguments of a route are bound, decided once per route instead of for every request:
 * the binder of each argument, when it runs, and whether the argument can be a path variable.
 * A route builds it on its first request, for the binder registry the request is bound with.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteShape {

    final RequestBinderRegistry registry;
    /**
     * The class of the match the path variables were read from. The path variables of another
     * kind of match are looked up for every argument.
     */
    final Class<?> matchType;
    final RequestArgumentBinder<Object>[] binders;
    final Slot[] slots;
    /**
     * Per argument, whether the template of the route has a variable of its name. {@code null}
     * when the variables of the match are not known, so every argument is looked up.
     */
    final boolean @Nullable [] pathVariables;
    /**
     * Whether any argument has a binder that runs after the filters.
     */
    final boolean hasPostponed;

    RouteShape(RequestBinderRegistry registry,
               RequestArgumentBinder<Object>[] binders,
               String[] argumentNames,
               Class<?> matchType,
               @Nullable Set<String> pathVariableNames) {
        this.registry = registry;
        this.matchType = matchType;
        this.binders = binders;
        int length = binders.length;
        this.slots = new Slot[length];
        boolean postponed = false;
        for (int i = 0; i < length; i++) {
            Slot slot = Slot.of(binders[i]);
            slots[i] = slot;
            postponed |= slot == Slot.POSTPONED || slot == Slot.POSTPONED_AND_EARLY;
        }
        this.hasPostponed = postponed;
        if (pathVariableNames == null) {
            this.pathVariables = null;
        } else {
            boolean[] variables = new boolean[length];
            for (int i = 0; i < length; i++) {
                variables[i] = pathVariableNames.contains(argumentNames[i]);
            }
            this.pathVariables = variables;
        }
    }

    /**
     * When the binder of an argument runs.
     */
    enum Slot {
        /**
         * The binder runs before the filters.
         */
        EARLY,
        /**
         * The binder runs after the filters.
         */
        POSTPONED,
        /**
         * The binder runs after the filters, and also before them (an unmatched argument).
         */
        POSTPONED_AND_EARLY,
        /**
         * There is no binder.
         */
        NONE;

        static Slot of(@Nullable RequestArgumentBinder<Object> binder) {
            if (binder == null) {
                return NONE;
            }
            if (binder instanceof PostponedRequestArgumentBinder<?>) {
                // Allow for the unmatched request argument binder to run even so it's postponed
                return binder instanceof UnmatchedRequestArgumentBinder ? POSTPONED_AND_EARLY : POSTPONED;
            }
            return EARLY;
        }
    }
}
