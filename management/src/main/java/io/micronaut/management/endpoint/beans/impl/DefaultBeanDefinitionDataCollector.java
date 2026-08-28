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

import io.micronaut.context.BeanContext;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.management.endpoint.beans.BeanDefinitionData;
import io.micronaut.management.endpoint.beans.BeanDefinitionDataCollector;
import io.micronaut.management.endpoint.beans.BeansEndpoint;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

    private final BeanContext beanContext;
    private final BeanDefinitionData<Map<String, Object>> beanDefinitionData;

    /**
     * @param beanContext The bean context
     * @param beanDefinitionData The {@link BeanDefinitionData}
     */
    DefaultBeanDefinitionDataCollector(BeanContext beanContext, BeanDefinitionData<Map<String, Object>> beanDefinitionData) {
        this.beanContext = beanContext;
        this.beanDefinitionData = beanDefinitionData;
    }

    @Override
    public Map<String, Object> getData() {
        Map<String, Object> beanData = new LinkedHashMap<>(1);
        List<BeanDefinition<?>> beanDefinitions = beanContext.getAllBeanDefinitions()
            .stream()
            .sorted(Comparator.comparing((BeanDefinition<?> bd) -> bd.getClass().getName()))
            .collect(Collectors.toUnmodifiableList());

        beanData.put("beans", getBeans(beanDefinitions));
        beanData.put("disabled", getDisabledBeans());
        return beanData;
    }

    /**
     * @param definitions The bean definitions
     * @return A map of bean information.
     */
    protected Map<String, Map<String, Object>> getBeans(Collection<BeanDefinition<?>> definitions) {
        AtomicInteger atomicInteger = new AtomicInteger();
        return definitions.stream()
            .collect(Collectors.toMap(definition -> {
                if (definition instanceof RuntimeBeanDefinition<?> runtimeBeanDefinition) {
                    return  RuntimeBeanDefinition.class.getName() + "#" + atomicInteger.getAndIncrement();
                }
                return definition.getClass().getName();
            }, beanDefinitionData::getData));
    }

    /**
     * @return Information about the disabled beans.
     */
    protected List<Map<String, Object>> getDisabledBeans() {
        Collection<DisabledBean<?>> disabledBeans = beanContext.getDisabledBeans();

        return disabledBeans.stream()
            .map(disabledBean -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("type", disabledBean.type().getTypeName());
                Qualifier<?> q = disabledBean.qualifier();
                if (q != null) {
                    data.put("qualifier", q.toString());
                }
                data.put("reasons", disabledBean.reasons());
                return data;
            }).toList();
    }
}
