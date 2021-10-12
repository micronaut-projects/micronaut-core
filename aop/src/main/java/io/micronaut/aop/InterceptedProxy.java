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

import io.micronaut.inject.qualifiers.Qualified;

/**
 * A {@link Intercepted} that proxies another instance.
 *
 * @param <T> The declaring type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InterceptedProxy<T> extends Intercepted, Qualified<T> {

    /**
     * This method will return the target object being proxied.
     *
     * @return The proxy target
     */
    T interceptedTarget();

}
