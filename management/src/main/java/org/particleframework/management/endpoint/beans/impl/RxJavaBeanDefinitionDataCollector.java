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
package org.particleframework.management.endpoint.beans.impl;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.particleframework.context.annotation.Requires;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.management.endpoint.beans.BeanDefinitionData;
import org.particleframework.management.endpoint.beans.BeanDefinitionDataCollector;
import org.particleframework.management.endpoint.beans.BeansEndpoint;
import org.particleframework.runtime.executor.IOExecutorServiceConfig;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The default {@link BeanDefinitionDataCollector} implementation. Returns a {@link Map} with
 * a single key, "beans" that has a key of the bean definition class name.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(beans = BeansEndpoint.class)
public class RxJavaBeanDefinitionDataCollector implements BeanDefinitionDataCollector<Map<String, Object>> {

    private BeanDefinitionData beanDefinitionData;
    private ExecutorService executorService;

    RxJavaBeanDefinitionDataCollector(BeanDefinitionData beanDefinitionData,
                                      @Named(IOExecutorServiceConfig.NAME) ExecutorService executorService) {
        this.beanDefinitionData = beanDefinitionData;
        this.executorService = executorService;
    }

    @Override
    public Publisher<Map<String, Object>> getData(Collection<BeanDefinition<?>> beanDefinitions) {
        return getBeans(beanDefinitions).map((beans) -> {
            Map<String, Object> beanData = new LinkedHashMap<>(1);
            beanData.put("beans", beans);
            return beanData;
        }).toFlowable();
    }

    protected Single<Map<String, Object>> getBeans(Collection<BeanDefinition<?>> definitions) {
        Map<String, Object> beans = new ConcurrentHashMap<>(definitions.size());

        return Flowable.fromIterable(definitions)
                .subscribeOn(Schedulers.from(executorService))
                .collectInto(beans, (map, definition) -> {
            map.put(definition.getClass().getName(), beanDefinitionData.getData(definition));
        });
    }
}
