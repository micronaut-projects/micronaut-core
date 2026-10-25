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
package io.micronaut.context.python;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.python.annotation.EventLoopSafe;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.MethodReference;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.executor.DefaultExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Routes generated Python controller methods to the IO executor while leaving
 * Micronaut's default executor selection available for Java code. A Python method
 * annotated with {@link ExecuteOn} runs on the executor it names, as a Java method does.
 * A method whose statically compiled body never enters Python ({@link EventLoopSafe})
 * stays on the event loop when the default selection keeps a Java method there.
 */
@Singleton
@Replaces(DefaultExecutorSelector.class)
final class PythonExecutorSelector extends DefaultExecutorSelector {

    private final Supplier<ExecutorService> ioExecutor;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator
     * @param ioExecutor The IO executor
     * @param blockingExecutor The blocking executor
     */
    @Inject
    PythonExecutorSelector(
        BeanLocator beanLocator,
        @Named(TaskExecutors.IO) BeanProvider<ExecutorService> ioExecutor,
        @Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> blockingExecutor) {
        super(beanLocator, ioExecutor, blockingExecutor);
        this.ioExecutor = SupplierUtil.memoized(ioExecutor::get);
    }

    @Override
    public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection) {
        // @ExecuteOn names the executor of a Python method as it does for a Java one
        if (isPythonMethod(method) && !Objects.requireNonNull(method).hasStereotype(ExecuteOn.class)) {
            TypeInformation<?> returnType = method.getReturnType();
            if (!returnType.isAsyncOrReactive()) {
                if (method.hasAnnotation(EventLoopSafe.class)) {
                    // a compiled body that runs no Python runs where a Java method runs; when the default
                    // selection offloads it (to the blocking executor, which may use virtual threads), the
                    // IO executor runs it as any other Python method: a fixed attribute is read from the
                    // Python object the first time, which a virtual thread cannot do
                    Optional<ExecutorService> selected = super.select(method, threadSelection);
                    if (selected.isEmpty()) {
                        return selected;
                    }
                }
                return Optional.of(ioExecutor.get());
            }
        }
        return super.select(method, threadSelection);
    }

    private static boolean isPythonMethod(@Nullable MethodReference<?, ?> method) {
        if (method == null) {
            return false;
        }
        Class<?> declaringType = method.getDeclaringType();
        return declaringType.isAnnotationPresent(PythonClass.class)
            || PooledValueCoercible.class.isAssignableFrom(declaringType);
    }
}
