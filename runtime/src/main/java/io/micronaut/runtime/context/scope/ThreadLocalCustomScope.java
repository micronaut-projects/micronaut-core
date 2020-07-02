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
package io.micronaut.runtime.context.scope;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link CustomScope} that stores values in thread local storage.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class ThreadLocalCustomScope implements CustomScope<ThreadLocal>, LifeCycle<ThreadLocalCustomScope> {

    private final java.lang.ThreadLocal<Map<String, Object>> threadScope = java.lang.ThreadLocal.withInitial(HashMap::new);

    @Override
    public Class<ThreadLocal> annotationType() {
        return ThreadLocal.class;
    }

    @Override
    public <T> T get(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        Map<String, Object> values = threadScope.get();
        String key = identifier.toString();
        T bean = (T) values.get(key);
        if (bean == null) {
            bean = provider.get();
            values.put(key, bean);
        }
        return bean;
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        Map<String, Object> values = threadScope.get();
        T previous = (T) values.remove(identifier.toString());
        return previous != null ? Optional.of(previous) : Optional.empty();
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public ThreadLocalCustomScope start() {
        return this;
    }

    @Override
    public ThreadLocalCustomScope stop() {
        threadScope.remove();
        return this;
    }
}
