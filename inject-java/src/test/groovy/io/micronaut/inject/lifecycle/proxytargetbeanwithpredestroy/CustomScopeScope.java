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
package io.micronaut.inject.lifecycle.proxytargetbeanwithpredestroy;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import org.jspecify.annotations.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class CustomScopeScope implements CustomScope<io.micronaut.inject.lifecycle.proxytargetbeanwithpredestroy.CustomScope>, LifeCycle<CustomScopeScope> {

    private final BeanContext beanContext;
    private List<BeanRegistration> beans = new ArrayList<>();

    public CustomScopeScope(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Class<io.micronaut.inject.lifecycle.proxytargetbeanwithpredestroy.CustomScope> annotationType() {
        return io.micronaut.inject.lifecycle.proxytargetbeanwithpredestroy.CustomScope.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return this.<T>findById(creationContext.id()).orElseGet(() -> {
            CreatedBean<T> createdBean = creationContext.create();
            BeanRegistration<T> br = (BeanRegistration<T>) createdBean;
            beans.add(br);
            return br;
        }).getBean();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        Optional<BeanRegistration<T>> found = findById(identifier);
        found.ifPresent(tBeanRegistration -> beans.remove(tBeanRegistration));
        return found.map(BeanRegistration::getBean);
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(BeanDefinition<T> beanDefinition) {
        return findByBeanDefinition(beanDefinition);
    }

    private <T> Optional<BeanRegistration<T>> findByBeanDefinition(BeanDefinition<T> beanDefinition) {
        return beans.stream().filter(br -> br.getBeanDefinition().equals(beanDefinition)).map(br -> (BeanRegistration<T>) br).findFirst();
    }

    @NonNull
    private <T> Optional<BeanRegistration<T>> findById(BeanIdentifier identifier) {
        return beans.stream().filter(br -> br.getIdentifier().equals(identifier)).map(br -> (BeanRegistration<T>) br).findFirst();
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public CustomScopeScope stop() {
        beans.forEach(beanContext::destroyBean);
        beans.clear();
        return this;
    }
}
