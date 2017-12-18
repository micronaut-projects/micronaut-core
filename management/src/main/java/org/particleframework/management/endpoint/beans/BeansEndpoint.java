/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.beans;

import org.particleframework.context.BeanContext;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Read;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the beans of the application.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint("beans")
public class BeansEndpoint {

    private BeanContext beanContext;
    private BeanDefinitionDataCollector beanDefinitionDataCollector;

    public BeansEndpoint(BeanContext beanContext, BeanDefinitionDataCollector beanAggregator) {
        this.beanContext = beanContext;
        this.beanDefinitionDataCollector = beanAggregator;
    }

    @Read
    public Object getBeans() {
        return beanDefinitionDataCollector.getData(beanContext.getAllBeanDefinitions());
    }
}
