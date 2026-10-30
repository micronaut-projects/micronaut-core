/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.scheduling.executor;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Replaces the executor factory and overrides the single-argument executor service method, as an application written
 * against it does, through the constructor that takes the bean locator. The executor factory's methods produce the
 * executor services on this replacement.
 */
@Singleton
@Replaces(ExecutorFactory.class)
@Requires(property = "spec.name", value = "ExecutorFactoryOverrideSpec")
@Requires(property = "spec.variant", value = "plain")
@SuppressWarnings("deprecation")
public class OverridingExecutorFactory extends ExecutorFactory {
    public static final List<String> CREATED = new CopyOnWriteArrayList<>();

    public OverridingExecutorFactory(BeanLocator beanLocator, ThreadFactory threadFactory) {
        super(beanLocator, threadFactory);
    }

    @Override
    public ExecutorService executorService(ExecutorConfiguration executorConfiguration) {
        CREATED.add(executorConfiguration.getName());
        return super.executorService(executorConfiguration);
    }
}
