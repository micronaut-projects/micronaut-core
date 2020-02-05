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
package io.micronaut.runtime.http.scope;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link CustomScope} that creates a new bean for every HTTP request.
 *
 * @author James Kleeh
 * @author Marcel Overdijk
 * @since 1.2.0
 */
@Singleton
class RequestCustomScope implements CustomScope<RequestScope>, LifeCycle<RequestCustomScope>, ApplicationEventListener<HttpRequestTerminatedEvent> {
    /**
     * The request attribute to store scoped beans in.
     */
    public static final String SCOPED_BEANS_ATTRIBUTE = "io.micronaut.http.SCOPED_BEANS";

    private static final Logger LOG = LoggerFactory.getLogger(RequestCustomScope.class);

    private final BeanContext beanContext;

    /**
     * Creates the request scope for the given context.
     *
     * @param beanContext The context
     */
    public RequestCustomScope(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Class<RequestScope> annotationType() {
        return RequestScope.class;
    }

    @Override
    public <T> T get(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition,
                     BeanIdentifier identifier, Provider<T> provider) {
        Optional<HttpRequest<T>> currentRequest = ServerRequestContext.currentRequest();
        if (!currentRequest.isPresent()) {
            throw new NoSuchBeanException(beanDefinition.getBeanType(), Qualifiers.byStereotype(RequestScope.class));
        }
        HttpRequest<T> httpRequest = currentRequest.get();
        Map scopedBeanMap = getRequestScopedBeans(httpRequest);
        T bean = (T) scopedBeanMap.get(identifier);
        if (bean == null) {
            synchronized (this) { // double check
                bean = (T) scopedBeanMap.get(identifier);
                if (bean == null) {
                    bean = provider.get();
                    if (bean instanceof RequestAware) {
                        ((RequestAware) bean).setRequest(httpRequest);
                    }
                    scopedBeanMap.put(identifier, bean);
                }
            }
        }
        return bean;
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return ServerRequestContext.currentRequest()
                .map(this::getRequestScopedBeans)
                .flatMap(m -> destroyRequestScopedBean(identifier, m));
    }

    private <T> Optional<T> destroyRequestScopedBean(BeanIdentifier identifier, Map m) {
        T bean = (T) m.remove(identifier);
        if (bean != null) {
            beanContext.findBeanDefinition(bean.getClass())
                    .ifPresent(definition -> {
                                if (definition instanceof DisposableBeanDefinition) {
                                    try {
                                        ((DisposableBeanDefinition<T>) definition).dispose(
                                                beanContext, bean
                                        );
                                    } catch (Exception e) {
                                        if (LOG.isErrorEnabled()) {
                                            LOG.error("Error disposing of request scoped bean: " + bean, e);
                                        }
                                    }
                                }
                            }

                    );
        }
        return Optional.ofNullable(bean);
    }

    @NonNull
    @Override
    public RequestCustomScope stop() {
        ServerRequestContext.currentRequest().ifPresent(this::destroyBeans);
        return this;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        destroyBeans(event.getSource());
    }

    /**
     * Destroys the request scoped beans for the given request.
     * @param request The request
     */
    private void destroyBeans(HttpRequest<?> request) {
        ArgumentUtils.requireNonNull("request", request);
        Map beans = getRequestScopedBeans(request);
        if (CollectionUtils.isNotEmpty(beans)) {
            beans.keySet().forEach(o -> {
                if (o instanceof BeanIdentifier) {
                    destroyRequestScopedBean((BeanIdentifier) o, beans);
                }
            });
        }
    }

    private synchronized <T> Map getRequestScopedBeans(HttpRequest<T> httpRequest) {
        MutableConvertibleValues<Object> attrs = httpRequest.getAttributes();
        return attrs.get(SCOPED_BEANS_ATTRIBUTE, Object.class).flatMap(o -> {
            if (o instanceof Map) {
                return Optional.of((Map) o);
            }
            return Optional.empty();
        }).orElseGet(() -> {
            Map scopedBeans = new ConcurrentHashMap(5);
            attrs.put(SCOPED_BEANS_ATTRIBUTE, scopedBeans);
            return scopedBeans;
        });
    }
}
