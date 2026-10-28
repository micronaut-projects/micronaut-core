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

import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.UnmatchedRequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.UnsafeExecutionHandle;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared argument metadata. Values and pending results belong to the match, never this plan.
 *
 * @param <T> The target type
 * @param <R> The result type
 */
final class RouteArgumentPlan<T, R> {
    final MethodExecutionHandle<T, R> methodExecutionHandle;
    final @Nullable UnsafeExecutionHandle<T, R> unsafeMethodExecutionHandle;
    final ExecutableMethod<T, R> executableMethod;
    final Argument<?>[] arguments;
    final String[] argumentNames;
    final Class<?>[] wrapperTypes;
    private final AtomicReference<@Nullable Binders> binders = new AtomicReference<>();

    RouteArgumentPlan(MethodBasedRouteInfo<T, R> routeInfo) {
        methodExecutionHandle = routeInfo.getTargetMethod();
        executableMethod = methodExecutionHandle.getExecutableMethod();
        unsafeMethodExecutionHandle = methodExecutionHandle instanceof UnsafeExecutionHandle<T, R> unsafe ? unsafe : null;
        arguments = executableMethod.getArguments();
        argumentNames = routeInfo.getArgumentNames();
        wrapperTypes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            wrapperTypes[i] = arguments[i].getWrapperType();
        }
    }

    Binders binders(MethodBasedRouteInfo<T, R> routeInfo, RequestBinderRegistry registry) {
        if (!(routeInfo instanceof DefaultMethodBasedRouteInfo<?, ?>)) {
            return new Binders(routeInfo.resolveArgumentBinders(registry));
        }
        Binders resolved = binders.get();
        if (resolved == null) {
            synchronized (this) {
                resolved = binders.get();
                if (resolved == null) {
                    RequestArgumentBinder<Object>[] all = new RequestArgumentBinder[arguments.length];
                    for (int i = 0; i < arguments.length; i++) {
                        all[i] = (RequestArgumentBinder<Object>) registry.findArgumentBinder(arguments[i]).orElse(null);
                    }
                    resolved = new Binders(all);
                    binders.set(resolved);
                }
            }
        }
        return resolved;
    }

    static final class Binders {
        final RequestArgumentBinder<Object>[] all;
        final RequestArgumentBinder<Object>[] before;
        final PostponedRequestArgumentBinder<Object>[] after;

        Binders(RequestArgumentBinder<Object>[] all) {
            this.all = all;
            before = new RequestArgumentBinder[all.length];
            after = new PostponedRequestArgumentBinder[all.length];
            for (int i = 0; i < all.length; i++) {
                RequestArgumentBinder<Object> binder = all[i];
                if (binder instanceof PostponedRequestArgumentBinder<Object> postponed) {
                    after[i] = postponed;
                    if (binder instanceof UnmatchedRequestArgumentBinder) {
                        before[i] = binder;
                    }
                } else {
                    before[i] = binder;
                }
            }
        }
    }
}
