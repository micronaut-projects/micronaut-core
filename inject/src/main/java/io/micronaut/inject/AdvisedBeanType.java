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
package io.micronaut.inject;

/**
 * A marker interface for all {@link BeanDefinitionReference} and {@link BeanDefinition}
 * instances to implement that provides access to the target bean type for an AOP advice bean.
 *
 * @since 2.1.1
 * @author graemerocher
 * @param <T> The bean type of the aspect
 */
public interface AdvisedBeanType<T> extends BeanType<T> {
    /**
     * Returns the target type for AOP advice. In the case of Introduction advice,
     * this is the interface the advice is declared on. In this case of Around advice
     * this the class the advice is declared on.
     *
     * @return The target type
     */
    Class<? super T> getInterceptedType();
}
