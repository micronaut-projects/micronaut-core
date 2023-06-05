/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Mutable propagated context will modify the internal context
 * Intended for use-cases when the propagated context needs to be mutated and propagated later.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public interface MutablePropagatedContext {

    /**
     * Creates a mutable propagated context with the initial context.
     *
     * @param propagatedContext The initial context
     * @return new mutable propagated context
     */
    @NonNull
    static MutablePropagatedContext of(@NonNull PropagatedContext propagatedContext) {
        return new MutablePropagatedContextImpl(propagatedContext);
    }

    /**
     * Modifies the context by adding an element.
     *
     * @param element The element element to be added
     * @return the current mutable propagated context.
     */
    @NonNull
    MutablePropagatedContext add(@NonNull PropagatedContextElement element);

    /**
     * Modifies the context by removing the provided element.
     *
     * @param element The context element to be removed
     * @return the current mutable propagated context.
     */
    @NonNull
    MutablePropagatedContext remove(@NonNull PropagatedContextElement element);

    /**
     * Modifies the context by replacing the provided element.
     *
     * @param oldElement The context element to be replaced
     * @param newElement The context element to be replaced with
     * @return the current mutable propagated context.
     */
    @NonNull
    MutablePropagatedContext replace(@NonNull PropagatedContextElement oldElement,
                                     @NonNull PropagatedContextElement newElement);

    /**
     * The mutated context.
     *
     * @return The propagated context that is being mutated.
     */
    @Nullable
    PropagatedContext getContext();

}
