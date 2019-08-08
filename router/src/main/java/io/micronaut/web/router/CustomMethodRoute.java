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
package io.micronaut.web.router;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.ExecutableMethod;

/**
 * A special class that routes everything non-standard for http.
 * @author spirit-1984
 * @since 1.2.1
 */
class CustomMethodRoute implements UriRoute {
    private final String methodName;
    private final UriRoute delegate;

    /**
     *
     * @param methodName Method name.
     * @param delegate The uri route that we actually have.
     */
    CustomMethodRoute(String methodName, UriRoute delegate) {
        this.methodName = methodName;
        this.delegate = delegate;
    }

    @Override
    public String getHttpMethodName() {
        return methodName;
    }

    @Override
    public UriRoute nest(Runnable nested) {
        delegate.nest(nested);
        return this;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return delegate.getHttpMethod();
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return delegate.getUriMatchTemplate();
    }

    @Override
    public Optional<UriRouteMatch> match(String uri) {
        return delegate
                .match(uri)
                .map(this::transform);
    }

    private UriRouteMatch transform(UriRouteMatch delegate) {
        return new UriRouteMatch() {

            @Override
            public UriRoute getRoute() {
                return CustomMethodRoute.this;
            }

            @Override
            public HttpMethod getHttpMethod() {
                return delegate.getHttpMethod();
            }

            @Override
            public UriRouteMatch fulfill(Map argumentValues) {
                return delegate.fulfill(argumentValues);
            }

            @Override
            public UriRouteMatch decorate(Function executor) {
                return delegate.decorate(executor);
            }

            @Nonnull
            @Override
            public ExecutableMethod getExecutableMethod() {
                return delegate.getExecutableMethod();
            }

            @Override
            public Object getTarget() {
                return delegate.getTarget();
            }

            @Override
            public Argument[] getArguments() {
                return delegate.getArguments();
            }

            @Override
            public Object invoke(Object... arguments) {
                return delegate.invoke(arguments);
            }

            @Override
            public Method getTargetMethod() {
                return delegate.getTargetMethod();
            }

            @Override
            public String getMethodName() {
                return delegate.getMethodName();
            }

            @Override
            public String getUri() {
                return delegate.getUri();
            }

            @Override
            public Map<String, Object> getVariableValues() {
                return delegate.getVariableValues();
            }

            @Override
            public List<UriMatchVariable> getVariables() {
                return delegate.getVariables();
            }

            @Override
            public Class<?> getDeclaringType() {
                return delegate.getDeclaringType();
            }

            @Override
            public Object execute(Map argumentValues) {
                return delegate.execute(argumentValues);
            }

            @Override
            public Optional<Argument<?>> getRequiredInput(String name) {
                return delegate.getRequiredInput(name);
            }

            @Override
            public Optional<Argument<?>> getBodyArgument() {
                return delegate.getBodyArgument();
            }

            @Override
            public List<MediaType> getProduces() {
                return delegate.getProduces();
            }

            @Override
            public ReturnType getReturnType() {
                return delegate.getReturnType();
            }

            @Override
            public boolean accept(@Nullable MediaType contentType) {
                return delegate.accept(contentType);
            }

            @Override
            public boolean test(Object o) {
                return delegate.test(o);
            }
        };
    }

    @Override
    public UriRoute consumes(MediaType... mediaType) {
        delegate.consumes(mediaType);
        return this;
    }

    @Override
    public UriRoute produces(MediaType... mediaType) {
        delegate.produces(mediaType);
        return this;
    }

    @Override
    public UriRoute acceptAll() {
        delegate.acceptAll();
        return this;
    }

    @Override
    public UriRoute where(Predicate<HttpRequest<?>> condition) {
        delegate.where(condition);
        return this;
    }

    @Override
    public UriRoute body(String argument) {
        delegate.body(argument);
        return this;
    }

    @Override
    public Route body(Argument<?> argument) {
        delegate.body(argument);
        return this;
    }

    @Override
    public int compareTo(UriRoute o) {
        return delegate.compareTo(o);
    }
}
