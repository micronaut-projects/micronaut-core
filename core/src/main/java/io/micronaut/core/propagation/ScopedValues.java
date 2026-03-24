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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Internal
final class ScopedValues {

    private static final ScopedValue<PropagatedContext> PROPAGATED_CONTEXT = ScopedValue.newInstance();

    @Nullable
    static PropagatedContext get() {
        if (PROPAGATED_CONTEXT.isBound()) {
            return PROPAGATED_CONTEXT.get();
        }
        return null;
    }

    static <T> T propagate(PropagatedContext propagatedContext, Supplier<T> supplier) {
        return ScopedValue.where(PROPAGATED_CONTEXT, propagatedContext).call(supplier::get);
    }

    static <T> T propagate(PropagatedContext propagatedContext, Callable<T> callable) throws Exception {
        return ScopedValue.where(PROPAGATED_CONTEXT, propagatedContext).call(callable::call);
    }

    static void propagate(PropagatedContext propagatedContext, Runnable runnable) {
        ScopedValue.where(PROPAGATED_CONTEXT, propagatedContext).run(runnable);
    }

}
