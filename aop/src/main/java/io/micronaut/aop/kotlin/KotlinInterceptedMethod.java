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
package io.micronaut.aop.kotlin;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import kotlin.coroutines.CoroutineContext;

/**
 * Kotlin's {@link InterceptedMethod} with extra methods to access coroutine's context.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface KotlinInterceptedMethod extends InterceptedMethod {

    /**
     * @return Coroutine's context
     */
    @NonNull
    CoroutineContext getCoroutineContext();

    /**
     * Update coroutine's context.
     *
     * @param coroutineContext The context
     */
    void updateCoroutineContext(@NonNull CoroutineContext coroutineContext);

}
