/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.websocket.context;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.websocket.annotation.*;
import io.micronaut.websocket.exceptions.WebSocketException;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link WebSocketBeanRegistry}.
 *
 * @author graemerocher
 * @since 1.0
 */
class DefaultWebSocketBeanRegistry implements WebSocketBeanRegistry {
    private final BeanContext beanContext;
    private final Class<? extends Annotation> stereotype;
    private final Map<Class, WebSocketBean> webSocketBeanMap = new ConcurrentHashMap<>(3);

    /**
     * Default constructor.
     * @param beanContext The bean context
     * @param stereotype Stereotype to use for bean lookup
     */
    DefaultWebSocketBeanRegistry(BeanContext beanContext, Class<? extends Annotation> stereotype) {
        this.beanContext = beanContext;
        this.stereotype = stereotype;
    }

    @Override
    public <T> WebSocketBean<T> getWebSocket(Class<T> type) {

        WebSocketBean webSocketBean = webSocketBeanMap.get(type);
        if (webSocketBean != null) {
            return webSocketBean;
        } else {

            Qualifier<T> qualifier = Qualifiers.byStereotype(stereotype);
            BeanDefinition<T> beanDefinition = beanContext.getBeanDefinition(type, qualifier);
            T bean = beanContext.getBean(type, qualifier);
            Collection<ExecutableMethod<T, ?>> executableMethods = beanDefinition.getExecutableMethods();
            MethodExecutionHandle<T, ?> onOpen = null;
            MethodExecutionHandle<T, ?> onClose = null;
            MethodExecutionHandle<T, ?> onMessage = null;
            MethodExecutionHandle<T, ?> onError = null;
            for (ExecutableMethod<T, ?> method : executableMethods) {
                if (method.isAnnotationPresent(OnOpen.class)) {
                    onOpen = ExecutionHandle.of(
                            bean,
                            method
                    );
                    continue;
                }

                if (method.isAnnotationPresent(OnClose.class)) {
                    onClose = ExecutionHandle.of(
                            bean,
                            method
                    );
                    continue;
                }

                if (method.isAnnotationPresent(OnError.class)) {
                    onError = ExecutionHandle.of(
                            bean,
                            method
                    );
                    continue;
                }

                if (method.isAnnotationPresent(OnMessage.class)) {
                    onMessage = ExecutionHandle.of(
                            bean,
                            method
                    );
                }
            }
            if (onMessage == null) {
                throw new WebSocketException("WebSocket handler must specify an @OnMessage handler: " + bean);
            }
            DefaultWebSocketBean<T> newWebSocketBean = new DefaultWebSocketBean<>(bean, beanDefinition, onOpen, onClose, onMessage, onError);
            if (beanDefinition.isSingleton()) {
                webSocketBeanMap.put(type, newWebSocketBean);
            }
            return newWebSocketBean;
        }
    }

    /**
     * Default web socket impl.
     *
     * @author graemerocher
     * @param <T>
     */
    private static class DefaultWebSocketBean<T> implements WebSocketBean<T> {
        private final T bean;
        private final BeanDefinition<T> definition;
        private final MethodExecutionHandle<T, ?> onOpen;
        private final MethodExecutionHandle<T, ?> onClose;
        private final MethodExecutionHandle<T, ?> onMessage;
        private final MethodExecutionHandle<T, ?> onError;

        DefaultWebSocketBean(T bean, BeanDefinition<T> definition, MethodExecutionHandle<T, ?> onOpen, MethodExecutionHandle<T, ?> onClose, MethodExecutionHandle<T, ?> onMessage, MethodExecutionHandle<T, ?> onError) {
            this.bean = bean;
            this.definition = definition;
            this.onOpen = onOpen;
            this.onClose = onClose;
            this.onMessage = onMessage;
            this.onError = onError;
        }

        @Override
        public BeanDefinition<T> getBeanDefinition() {
            return definition;
        }

        @Override
        public T getTarget() {
            return bean;
        }

        @Override
        public Optional<MethodExecutionHandle<T, ?>> messageMethod() {
            return Optional.of(onMessage);
        }

        @Override
        public Optional<MethodExecutionHandle<T, ?>> closeMethod() {
            return Optional.ofNullable(onClose);
        }

        @Override
        public Optional<MethodExecutionHandle<T, ?>> openMethod() {
            return Optional.ofNullable(onOpen);
        }

        @Override
        public Optional<MethodExecutionHandle<T, ?>> errorMethod() {
            return Optional.ofNullable(onError);
        }
    }
}
