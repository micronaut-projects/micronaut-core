/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.tracing.instrument;

import java.util.concurrent.Callable;

/**
 * The tracing wrapper of {@link Runnable} and {@link Callable}.
 *
 * @author dstepanov
 * @since 1.3
 */
public interface TracingWrapper {

    /**
     * Wraps {@link Runnable} for tracing.
     * @param runnable instance to be wrapped
     * @return wrapped instance
     */
    Runnable wrap(Runnable runnable);

    /**
     * Wraps {@link Callable} for tracing.
     * @param callable instance to be wrapped
     * @param <V> callable generic param
     * @return wrapped instance
     */
    <V> Callable<V> wrap(Callable<V> callable);
}
