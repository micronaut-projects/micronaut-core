/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.RetryState;

/**
 * Mutable retry state that can calculate the next retry delay.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public interface MutableRetryState extends RetryState {

    /**
     * Returns the millisecond value for the next delay.
     *
     * @return The next delay in milliseconds
     */
    long nextDelay();

    /**
     * Called when an attempt fails with an exception this state does not capture, see
     * {@link #getCapturedException()}, and which is rethrown without a retry.
     *
     * @param exception The exception
     * @since 5.3.0
     */
    default void onUncaptured(Throwable exception) {
    }

    /**
     * Called when the subscriber of a publisher cancels it before it produced a value. By
     * default it is a success, see {@link #close(Throwable)}.
     *
     * @since 5.3.0
     */
    default void onCancel() {
        close(null);
    }

    /**
     * Called when the operation ends without an outcome, e.g. its thread was interrupted while
     * it waited for a retry: a state that holds a permit returns it.
     *
     * @since 5.3.0
     */
    default void release() {
    }
}
