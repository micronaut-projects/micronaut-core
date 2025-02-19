/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * Additional interface that {@link io.micronaut.core.bind.ArgumentBinder}s can implement to
 * restrict when a filter method with this argument will run.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Experimental
public interface FilterArgumentBinderPredicate {
    /**
     * Check whether the filter method should run in the given context.
     *
     * @param argument                 The argument that this binder binds
     * @param mutablePropagatedContext The propagated context
     * @param request                  The request
     * @param response                 For response filters, the response (if there is no failure)
     * @param failure                  For response filters, the failure
     * @return {@code true} if this filter method should run
     */
    boolean test(Argument<?> argument,
                 MutablePropagatedContext mutablePropagatedContext,
                 HttpRequest<?> request,
                 @Nullable HttpResponse<?> response,
                 @Nullable Throwable failure);
}
