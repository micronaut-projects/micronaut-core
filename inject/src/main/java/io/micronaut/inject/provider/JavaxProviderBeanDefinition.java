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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Implementation for javax provider bean lookups.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class JavaxProviderBeanDefinition extends AbstractProviderDefinition<Object> {

    private static final String JAVAX_PROVIDER_CLASS_NAME = "javax.inject.Provider";

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return isTypePresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Object> getBeanType() {
        return (Class) providerType();
    }

    @Override
    public boolean isPresent() {
        return isTypePresent();
    }

    @Override
    protected Object buildProvider(BeanResolutionContext resolutionContext, BeanContext context, Argument<Object> argument, @Nullable Qualifier<Object> qualifier, boolean singleton) {
        Class<?> providerType = providerType();
        if (singleton) {
            return Proxy.newProxyInstance(
                providerType.getClassLoader(),
                new Class<?>[]{providerType},
                new SingletonProviderInvocationHandler(resolutionContext, argument, qualifier)
            );
        }
        return Proxy.newProxyInstance(
            providerType.getClassLoader(),
            new Class<?>[]{providerType},
            new ProviderInvocationHandler(resolutionContext, argument, qualifier)
        );
    }

    private static boolean isTypePresent() {
        return ClassUtils.isPresent(JAVAX_PROVIDER_CLASS_NAME, JavaxProviderBeanDefinition.class.getClassLoader());
    }

    private static Class<?> providerType() {
        return ClassUtils.forName(JAVAX_PROVIDER_CLASS_NAME, JavaxProviderBeanDefinition.class.getClassLoader())
            .orElseThrow(() -> new NoClassDefFoundError(JAVAX_PROVIDER_CLASS_NAME));
    }

    private static Object doGetBean(BeanResolutionContext resolutionContext, Argument<Object> argument, @Nullable Qualifier<Object> qualifier) {
        return resolutionContext.copy().getBean(argument, qualifier);
    }

    private abstract static class AbstractProviderInvocationHandler implements InvocationHandler {
        protected final BeanResolutionContext resolutionContext;
        protected final Argument<Object> argument;
        protected final @Nullable Qualifier<Object> qualifier;

        AbstractProviderInvocationHandler(BeanResolutionContext resolutionContext, Argument<Object> argument, @Nullable Qualifier<Object> qualifier) {
            this.resolutionContext = resolutionContext;
            this.argument = argument;
            this.qualifier = qualifier;
        }

        protected abstract Object get();

        @Override
        public final Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> JAVAX_PROVIDER_CLASS_NAME + " proxy for " + argument;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
                    default -> throw new UnsupportedOperationException("Unsupported method: " + method);
                };
            }
            if ("get".equals(method.getName()) && method.getParameterCount() == 0) {
                return get();
            }
            throw new UnsupportedOperationException("Unsupported method: " + method);
        }
    }

    private static final class ProviderInvocationHandler extends AbstractProviderInvocationHandler {
        ProviderInvocationHandler(BeanResolutionContext resolutionContext, Argument<Object> argument, @Nullable Qualifier<Object> qualifier) {
            super(resolutionContext, argument, qualifier);
        }

        @Override
        protected Object get() {
            return doGetBean(resolutionContext, argument, qualifier);
        }
    }

    private static final class SingletonProviderInvocationHandler extends AbstractProviderInvocationHandler {
        private @Nullable Object bean;

        SingletonProviderInvocationHandler(BeanResolutionContext resolutionContext, Argument<Object> argument, @Nullable Qualifier<Object> qualifier) {
            super(resolutionContext, argument, qualifier);
        }

        @Override
        protected Object get() {
            Object existing = bean;
            if (existing == null) {
                existing = doGetBean(resolutionContext, argument, qualifier);
                bean = existing;
            }
            return existing;
        }
    }
}
