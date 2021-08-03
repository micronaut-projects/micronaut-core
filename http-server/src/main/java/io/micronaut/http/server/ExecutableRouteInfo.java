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
package io.micronaut.http.server;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodReference;
import io.micronaut.web.router.RouteInfo;

import java.lang.reflect.Method;

class ExecutableRouteInfo<T> implements RouteInfo<Object>, MethodReference<T, Object> {

    private final ExecutableMethod<T, Object> method;
    private final boolean errorRoute;

    ExecutableRouteInfo(ExecutableMethod<T, Object> method,
                        boolean errorRoute) {
        this.method = method;
        this.errorRoute = errorRoute;
    }

    @Override
    public Argument<?>[] getArguments() {
        return method.getArguments();
    }

    @Override
    public Method getTargetMethod() {
        return method.getTargetMethod();
    }

    @Override
    public ReturnType<Object> getReturnType() {
        return method.getReturnType();
    }

    @Override
    public Class<T> getDeclaringType() {
        return method.getDeclaringType();
    }

    @Override
    public String getMethodName() {
        return method.getMethodName();
    }

    @Override
    public boolean isErrorRoute() {
        return errorRoute;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return method.getAnnotationMetadata();
    }
}
