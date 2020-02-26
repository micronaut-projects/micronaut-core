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
package io.micronaut.inject.writer;

/**
 * Extends {@link BeanDefinitionVisitor} and adds access to the proxied type name.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProxyingBeanDefinitionVisitor extends BeanDefinitionVisitor {

    /**
     * @return The fully qualified name of the class being proxied
     */
    String getProxiedTypeName();

    /**
     * @return The bean definition that is proxied
     */
    String getProxiedBeanDefinitionName();
}
