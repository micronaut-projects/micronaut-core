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
package io.micronaut.configuration.jmx.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Contract for creating dynamic management beans from a bean definition.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface DynamicMBeanFactory {

    /**
     * Creates a dynamic management bean from the provided bean definition.
     *
     * @param beanDefinition   The bean definition
     * @param instanceSupplier The supplier of the instance to execute the methods on
     * @return The dynamic management bean
     */
    default Object createMBean(BeanDefinition beanDefinition, Supplier<Object> instanceSupplier) {
        return createMBean(beanDefinition, beanDefinition.getExecutableMethods(), instanceSupplier);
    }

    /**
     * Creates a dynamic management bean from the provided bean definition and methods.
     *
     * @param beanDefinition   The bean definition
     * @param methods          The methods to be made available as operations
     * @param instanceSupplier The supplier of the instance to execute the methods on
     * @return The dynamic management bean
     */
    Object createMBean(BeanDefinition beanDefinition, Collection<ExecutableMethod> methods, Supplier<Object> instanceSupplier);

}
