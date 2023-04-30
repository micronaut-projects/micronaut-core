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
package io.micronaut.web.router;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.inject.MethodExecutionHandle;

/**
 * Represents a route that is backed by a method.
 *
 * @param <T> The target
 * @param <R> The result
 * @author James Kleeh
 * @since 1.0
 */
public interface MethodBasedRouteInfo<T, R> extends RouteInfo<R> {

    /**
     * @return The {@link MethodExecutionHandle}
     */
    MethodExecutionHandle<T, R> getTargetMethod();

    @NonNull
    String[] getArgumentNames();

    RequestArgumentBinder<Object>[] resolveArgumentBinders(RequestBinderRegistry requestBinderRegistry);

}
