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
package io.micronaut.runtime.context.scope;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * A {@link CustomScope} that creates a new bean for every HTTP request.
 *
 * @author James Kleeh
 * @author Marcel Overdijk
 * @since 1.2
 */
@Singleton
class RequestCustomScope implements CustomScope<Request>, LifeCycle<RequestCustomScope> {

    private WeakHashMap<HttpRequest, Map<String, Object>> requestScope = new WeakHashMap<>();

    @Override
    public Class<Request> annotationType() {
        return Request.class;
    }

    @Override
    public <T> T get(
            BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        Optional<HttpRequest<T>> currentRequest = ServerRequestContext.currentRequest();
        if (!currentRequest.isPresent()) {
            throw new NoSuchBeanException(beanDefinition.getBeanType(), Qualifiers.byStereotype(Request.class));
        }
        Map<String, Object> values = requestScope.get(currentRequest.get());
        if (values == null) {
            synchronized (this) { // double check
                values = requestScope.get(currentRequest.get());
                if (values == null) {
                    values = new HashMap<>();
                    requestScope.put(currentRequest.get(), values);
                }
            }
        }
        String key = identifier.toString();
        T bean = (T) values.get(key);
        if (bean == null) {
            synchronized (this) { // double check
                bean = (T) values.get(key);
                if (bean == null) {
                    bean = provider.get();
                    values.put(key, bean);
                }
            }
        }
        return bean;
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return ServerRequestContext.currentRequest()
                .map(requestScope::get)
                .flatMap(m -> Optional.ofNullable((T) m.remove(identifier.toString())));
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public RequestCustomScope start() {
        return this;
    }

    @Override
    public RequestCustomScope stop() {
        requestScope.clear();
        return this;
    }
}
