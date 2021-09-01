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
package io.micronaut.management.endpoint.beans.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.management.endpoint.beans.BeanDefinitionData;
import io.micronaut.management.endpoint.beans.BeanDefinitionDataCollector;
import io.micronaut.management.endpoint.beans.BeansEndpoint;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The default {@link BeanDefinitionDataCollector} implementation. Returns a {@link Map} with
 * a single key, "beans" that has a key of the bean definition class name.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(beans = BeansEndpoint.class)
public class DefaultBeanDefinitionDataCollector implements BeanDefinitionDataCollector<Map<String, Object>> {

    private BeanDefinitionData beanDefinitionData;

    /**
     * @param beanDefinitionData The {@link BeanDefinitionData}
     */
    DefaultBeanDefinitionDataCollector(BeanDefinitionData beanDefinitionData) {
        this.beanDefinitionData = beanDefinitionData;
    }

    @Override
    public Publisher<Map<String, Object>> getData(Collection<BeanDefinition<?>> beanDefinitions) {
        return Mono.from(getBeans(beanDefinitions)).map(beans -> {
            Map<String, Object> beanData = new LinkedHashMap<>(1);
            beanData.put("beans", beans);
            return beanData;
        });
    }

    /**
     * @param definitions The bean definitions
     * @return A {@link Publisher} that wraps a Map
     */
    protected Publisher<Map<String, Object>> getBeans(Collection<BeanDefinition<?>> definitions) {
        return Flux.fromIterable(definitions)
                .collectMap(definition -> definition.getClass().getName(),
                        definition -> {
                    return beanDefinitionData.getData(definition);
                });
    }
}
