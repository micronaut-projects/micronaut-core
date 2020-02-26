/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry.event;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.RetryState;

/**
 * An event fired when the Circuit is {@link io.micronaut.retry.CircuitState#OPEN} and
 * requests are no longer being accepted.
 *
 * @author graemerocher
 * @since 1.0
 */
public class CircuitOpenEvent extends ApplicationEvent {

    private final RetryState retryState;
    private final Throwable throwable;

    /**
     * @param source     A compile time produced invocation of a method call
     * @param retryState Encapsulate the current state of {@link io.micronaut.retry.annotation.Retryable} operation.
     * @param throwable  The cause
     */
    public CircuitOpenEvent(
        ExecutableMethod<?, ?> source,
        RetryState retryState,
        Throwable throwable) {

        super(source);
        this.retryState = retryState;
        this.throwable = throwable;
    }

    /**
     * @return The retry context
     */
    public RetryState getRetryState() {
        return retryState;
    }

    /**
     * @return The original exception that will be rethrown to the user
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * @return The method that represents the circuit
     */
    @Override
    public ExecutableMethod<?, ?> getSource() {
        return (ExecutableMethod<?, ?>) super.getSource();
    }
}
