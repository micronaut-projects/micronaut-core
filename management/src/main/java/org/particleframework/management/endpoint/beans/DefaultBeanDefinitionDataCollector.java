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

import org.particleframework.inject.BeanDefinition;

import javax.inject.Singleton;
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
public class DefaultBeanDefinitionDataCollector implements BeanDefinitionDataCollector<Map<String, Object>> {

    private BeanDefinitionData beanDefinitionData;

    DefaultBeanDefinitionDataCollector(BeanDefinitionData beanDefinitionData) {
        this.beanDefinitionData = beanDefinitionData;
    }

    @Override
    public Map<String, Object> getData(Collection<BeanDefinition<?>> beanDefinitions) {
        Map<String, Object> beanData = new LinkedHashMap<>(1);
        beanData.put("beans", getBeans(beanDefinitions));
        return beanData;
    }

    protected Map<String, Object> getBeans(Collection<BeanDefinition<?>> definitions) {
        Map<String, Object> beans = new LinkedHashMap<>(definitions.size());
        for (BeanDefinition<?> definition : definitions) {
            beans.put(definition.getClass().getName(), beanDefinitionData.getData(definition));
        }
        return beans;
    }
}
