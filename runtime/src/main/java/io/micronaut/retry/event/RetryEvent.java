/*
 * Copyright 2018 original authors
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
import io.micronaut.retry.RetryState;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.retry.RetryState;

/**
 * An event triggered on each retry
 *
 * @author graemerocher
 * @since 1.0
 */
public class RetryEvent extends ApplicationEvent {

    private final RetryState retryState;
    private final Throwable throwable;

    public RetryEvent(MethodInvocationContext<?,?> source, RetryState retryState, Throwable throwable) {
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
     * @return The exception that caused the retry
     */
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public MethodInvocationContext<?,?> getSource() {
        return (MethodInvocationContext<?, ?>) super.getSource();
    }
}
