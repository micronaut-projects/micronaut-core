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
package io.micronaut.aop;

/**
 * <p>Extended version of {@link InterceptedProxy} that allows swapping out the previous instance.</p>
 *
 * <p>At compile time an implementation is generated that uses a {@link java.util.concurrent.locks.ReentrantReadWriteLock} to maintain a reference to the target of the proxy</p>
 *
 * @param <T> The declaring type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HotSwappableInterceptedProxy<T> extends InterceptedProxy<T> {
    /**
     * Swaps the underlying proxied instance for a new instance.
     *
     * @param newInstance The new instance
     * @return The old instance
     */
    T swap(T newInstance);
}
