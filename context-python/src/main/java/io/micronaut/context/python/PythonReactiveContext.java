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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import org.jspecify.annotations.Nullable;

/**
 * The reactive context a subscriber (or the caller of an eager coroutine) hands to a Python
 * coroutine: the Reactor context of the subscription (a reactive transaction status lives there)
 * and the propagated context it carries. The asyncio task keeps it in a {@code contextvars}
 * variable, so every publisher the coroutine awaits is subscribed within it and every callback of
 * the task runs in its propagated context, whichever thread or task scheduled the callback.
 *
 * @param reactorContext The Reactor {@code reactor.util.context.Context} of the subscriber, or
 *                       {@code null} when Reactor is not on the class path or the coroutine was
 *                       started eagerly; typed as an object so the record loads without Reactor
 * @param propagatedContext The propagated context of the subscriber or caller
 * @since 5.2.0
 */
@Internal
public record PythonReactiveContext(@Nullable Object reactorContext, PropagatedContext propagatedContext) {

    /**
     * Run a callback of the task in the propagated context; called by the asyncio module for every
     * {@code call_soon}/{@code call_later} callback of a task that carries this context.
     *
     * @param callback The callback
     */
    public void run(Runnable callback) {
        try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
            callback.run();
        }
    }
}
