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
package io.micronaut.scheduling.instrument;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.Callable;

/**
 * Wrappes {@link Callable} and invokes {@link InvocationInstrumenter}.
 *
 * @param <V> callable generic parameter
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Internal
final class InvocationInstrumenterWrappedCallable<V> implements Callable<V> {

    private final InvocationInstrumenter invocationInstrumenter;
    private final Callable<V> callable;

    /**
     * @param invocationInstrumenter instrumenter to be invoked
     * @param callable               original callable
     */
    InvocationInstrumenterWrappedCallable(InvocationInstrumenter invocationInstrumenter, Callable<V> callable) {
        this.invocationInstrumenter = invocationInstrumenter;
        this.callable = callable;
    }

    /**
     * Wrapped call.
     *
     * @return new wrapped instance
     * @throws Exception if erro
     */
    @Override
    public V call() throws Exception {
        try {
            invocationInstrumenter.beforeInvocation();
            return callable.call();
        } finally {
            invocationInstrumenter.afterInvocation(true);
        }
    }

}
