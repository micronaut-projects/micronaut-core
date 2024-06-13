/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.qualifiers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;

/**
 * Qualifies the origin bean definition that was used to create an each bean.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
final class EachBeanQualifier<T> extends FilteringQualifier<T> {

    private final BeanDefinition<?> beanDefinition;

    EachBeanQualifier(BeanDefinition<?> beanDefinition) {
        this.beanDefinition = beanDefinition;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        return candidate.equals(beanDefinition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !EachBeanQualifier.class.isAssignableFrom(o.getClass())) {
            return false;
        }

        EachBeanQualifier<?> that = (EachBeanQualifier<?>) o;

        return beanDefinition.equals(that.beanDefinition);
    }

    @Override
    public String toString() {
        return "EachBeanQualifier('" + beanDefinition + "')";
    }

    @Override
    public int hashCode() {
        return beanDefinition.hashCode();
    }

}
