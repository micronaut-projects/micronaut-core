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
package io.micronaut.management.endpoint.beans;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the beans of the application.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint("beans")
public class BeansEndpoint {

    private final BeanDefinitionDataCollector<Object> beanDefinitionDataCollector;

    /**
     * @param beanDefinitionDataCollector The {@link BeanDefinitionDataCollector}
     */
    public BeansEndpoint(BeanDefinitionDataCollector<Object> beanDefinitionDataCollector) {
        this.beanDefinitionDataCollector = beanDefinitionDataCollector;
    }

    /**
     * @return A {@link org.reactivestreams.Publisher} with the beans
     */
    @Read
    @SingleResult
    public Object getBeans() {
        return beanDefinitionDataCollector.getData();
    }
}
