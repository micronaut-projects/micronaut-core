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
import io.micronaut.core.annotation.Nullable;

/**
 * Special version of {@link PropagatedContextElement} that is thread-aware and can update the thread context on the initial propagation entry
 * and restore it on the propagation exist.
 *
 * @param <S> The restore state type
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Experimental
public interface ThreadPropagatedContextElement<S> extends PropagatedContextElement {

    /**
     * Update the thread context on the propagation entry and return the state that will be used for the restore on the propagation exit.
     *
     * @return The state to be restored
     */
    @Nullable
    S updateThreadContext();

    /**
     * Restore the state on the propagation exit.
     *
     * @param oldState The state to be restored
     */
    void restoreThreadContext(@Nullable S oldState);

}
