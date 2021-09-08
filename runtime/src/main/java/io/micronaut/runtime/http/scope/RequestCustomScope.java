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
package io.micronaut.runtime.http.scope;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link io.micronaut.context.scope.CustomScope} that creates a new bean for every HTTP request.
 *
 * @author James Kleeh
 * @author Marcel Overdijk
 * @since 1.2.0
 */
@Singleton
class RequestCustomScope extends AbstractConcurrentCustomScope<RequestScope> implements ApplicationEventListener<HttpRequestTerminatedEvent> {
    /**
     * The request attribute to store scoped beans in.
     */
    public static final String SCOPED_BEANS_ATTRIBUTE = "io.micronaut.http.SCOPED_BEANS";

    /**
     * Creates the request scope for the given context.
     *
     */
    public RequestCustomScope() {
        super(RequestScope.class);
    }

    @Override
    public void close() {
        ServerRequestContext.currentRequest().ifPresent(this::destroyBeans);
    }

    @Override
    public boolean isRunning() {
        return ServerRequestContext.currentRequest().isPresent();
    }

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        destroyBeans(event.getSource());
    }

    @NonNull
    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        final HttpRequest<Object> request = ServerRequestContext.currentRequest().orElse(null);
        if (request != null) {
            //noinspection ConstantConditions
            return getRequestAttributeMap(request, forCreation);
        } else {
            throw new IllegalStateException("No request present");
        }
    }

    @NonNull
    @Override
    protected <T> CreatedBean<T> doCreate(@NonNull BeanCreationContext<T> creationContext) {
        final HttpRequest<Object> request = ServerRequestContext.currentRequest().orElse(null);
        final CreatedBean<T> createdBean = super.doCreate(creationContext);
        final T bean = createdBean.bean();
        if (bean instanceof RequestAware) {
            ((RequestAware) bean).setRequest(request);
        }
        return createdBean;
    }

    /**
     * Destroys the request scoped beans for the given request.
     * @param request The request
     */
    private void destroyBeans(HttpRequest<?> request) {
        ArgumentUtils.requireNonNull("request", request);
        ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> requestScopedBeans =
                getRequestAttributeMap(request, false);
        if (requestScopedBeans != null) {
            destroyScope(requestScopedBeans);
        }
    }

    private <T> ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> getRequestAttributeMap(HttpRequest<T> httpRequest, boolean create) {
        MutableConvertibleValues<Object> attrs = httpRequest.getAttributes();
        Object o = attrs.getValue(SCOPED_BEANS_ATTRIBUTE);
        if (o instanceof ConcurrentHashMap) {
            return (ConcurrentHashMap<BeanIdentifier, CreatedBean<?>>) o;
        }
        if (create) {
            ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans = new ConcurrentHashMap<>(5);
            attrs.put(SCOPED_BEANS_ATTRIBUTE, scopedBeans);
            return scopedBeans;
        }
        return null;
    }
}
