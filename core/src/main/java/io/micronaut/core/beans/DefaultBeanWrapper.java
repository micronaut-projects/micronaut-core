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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Default implementation of the {@link BeanWrapper} interface.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class DefaultBeanWrapper<T> implements BeanWrapper<T> {

    private final T bean;
    private final BeanIntrospection<T> introspection;

    /**
     * Default constructor.
     * @param bean The bean.
     * @param introspection The introspection.
     */
    DefaultBeanWrapper(@NonNull T bean, @NonNull BeanIntrospection<T> introspection) {
        ArgumentUtils.requireNonNull("bean", bean);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.bean = bean;
        this.introspection = introspection;
    }

    @NonNull
    @Override
    public BeanIntrospection<T> getIntrospection() {
        return introspection;
    }

    @NonNull
    @Override
    public T getBean() {
        return bean;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultBeanWrapper<?> that = (DefaultBeanWrapper<?>) o;
        return Objects.equals(bean, that.bean) &&
                Objects.equals(introspection, that.introspection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bean, introspection);
    }
}
