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

/**
 * Internal utilities that bridge {@link PropagatedContext} to JDK {@link ScopedValue} bindings.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
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
        ScopedValue.Carrier carrier = prepareCarrier(propagatedContext);
        return carrier.call(supplier::get);
    }

    static <T> T propagate(PropagatedContext propagatedContext, Callable<T> callable) throws Exception {
        ScopedValue.Carrier carrier = prepareCarrier(propagatedContext);
        return carrier.call(callable::call);
    }

    static void propagate(PropagatedContext propagatedContext, Runnable runnable) {
        ScopedValue.Carrier carrier = prepareCarrier(propagatedContext);
        carrier.run(runnable);
    }

    private static ScopedValue.Carrier prepareCarrier(PropagatedContext propagatedContext) {
        ScopedValue.Carrier carrier = ScopedValue.where(PROPAGATED_CONTEXT, propagatedContext);
        if (propagatedContext instanceof PropagatedContextImpl contextImpl && contextImpl.containsScopedValueElements) {
            for (PropagatedContextElement element : contextImpl.elements) {
                if (element instanceof ScopedValuePropagatedContextElement<?> scopedValueElement) {
                    carrier = bindScopedValue(carrier, scopedValueElement);
                }
            }
        }
        return carrier;
    }

    private static <T> ScopedValue.Carrier bindScopedValue(ScopedValue.Carrier carrier, ScopedValuePropagatedContextElement<T> element) {
        ScopedValue<T> scopedValue = element.scopedValue();
        T value = element.scopedValueValue();
        return carrier.where(scopedValue, value);
    }

}
