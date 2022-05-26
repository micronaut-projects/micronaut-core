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
package io.micronaut.scheduling.executor;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonBlocking;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Default implementation of the {@link ExecutorSelector} interface that regards methods that return reactive types as non-blocking.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultExecutorSelector implements ExecutorSelector {

    private static final String EXECUTE_ON = ExecuteOn.class.getName();
    private final BeanLocator beanLocator;
    private final Supplier<ExecutorService> ioExecutor;

    /**
     * Default constructor.
     * @param beanLocator The bean locator
     * @param ioExecutor The IO executor
     */
    @Inject
    protected DefaultExecutorSelector(BeanLocator beanLocator, @jakarta.inject.Named(TaskExecutors.IO) BeanProvider<ExecutorService> ioExecutor) {
        this.beanLocator = beanLocator;
        this.ioExecutor = SupplierUtil.memoized(ioExecutor::get);
    }

    @Override
    public Optional<ExecutorService> select(MethodReference method, ThreadSelection threadSelection) {
        final String name = method.stringValue(EXECUTE_ON).orElse(null);
        if (name != null) {
            final ExecutorService executorService;
            try {
                executorService = beanLocator.getBean(ExecutorService.class, Qualifiers.byName(name));
                return Optional.of(executorService);
            } catch (NoSuchBeanException e) {
                throw new SchedulerConfigurationException(
                        method,
                        "No executor configured for name: " + name
                );
            }
        } else if (threadSelection == ThreadSelection.AUTO) {
            if (method.hasStereotype(NonBlocking.class)) {
                return Optional.empty();
            } else if (method.hasStereotype(Blocking.class)) {
                return Optional.of(ioExecutor.get());
            } else {
                TypeInformation<?> returnType = method.getReturnType();
                if (returnType.isWrapperType()) {
                    Optional<Argument<?>> generic = method.getReturnType().getFirstTypeVariable();
                    if (generic.isPresent()) {
                        returnType = generic.get();
                    }
                }
                if (returnType.isAsyncOrReactive()) {
                    return Optional.empty();
                } else {
                    return Optional.of(ioExecutor.get());
                }
            }
        } else if (threadSelection == ThreadSelection.IO) {
            return Optional.of(ioExecutor.get());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutorService> select(String name) {
        return beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name));
    }
}
