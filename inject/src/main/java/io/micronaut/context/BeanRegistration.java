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
package io.micronaut.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import java.util.Objects;

/**
 * <p>A bean registration is an association between a {@link BeanDefinition} and a created bean, typically a
 * {@link javax.inject.Singleton}.</p>
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanRegistration<T> {
    final BeanIdentifier identifier;
    final BeanDefinition<T> beanDefinition;
    final T bean;

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     */
    public BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean) {
        this.identifier = identifier;
        this.beanDefinition = beanDefinition;
        this.bean = bean;
    }

    /**
     * @return Teh bean identifier
     */
    public BeanIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * @return The bean definition
     */
    public BeanDefinition<T> getBeanDefinition() {
        return beanDefinition;
    }

    /**
     * @return The bean instance
     */
    public T getBean() {
        return bean;
    }

    @Override
    public String toString() {
        return "BeanRegistration: " + bean;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BeanRegistration<?> that = (BeanRegistration<?>) o;
        return Objects.equals(identifier, that.identifier) &&
                Objects.equals(beanDefinition, that.beanDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, beanDefinition);
    }
}
