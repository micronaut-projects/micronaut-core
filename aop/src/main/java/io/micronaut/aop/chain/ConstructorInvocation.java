/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.aop.chain;

import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.core.annotation.Internal;

/**
 * A constructor invocation with the execution contract required by bean creation.
 *
 * @param <T> The constructed type
 * @since 5.3.0
 */
@Internal
public interface ConstructorInvocation<T> extends ConstructorInvocationContext<T> {
    /**
     * Executes construction advice, rejecting null results and distinguishing constructor-body failures
     * from advice failures. Unlike {@link #proceed()}, advice failures are carried to the bean creation boundary.
     *
     * @return The constructed bean
     */
    T instantiate();
}
