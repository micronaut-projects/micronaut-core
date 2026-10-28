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
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.python.annotation.EventLoopSafe;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.StringUtils;
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
 * stays on the event loop when the default selection keeps a Java method there and its bean, a
 * singleton, was created when the application started ({@link PythonEventLoopBeans}), unless
 * {@value #EVENT_LOOP_PROPERTY} is {@code false}.
 */
@Singleton
@Replaces(DefaultExecutorSelector.class)
final class PythonExecutorSelector extends DefaultExecutorSelector {

    /**
     * Whether a route whose compiled body never enters Python stays on the event loop, as a Java
     * route does; {@code false} runs it on the IO executor as an interpreted route. Defaults to {@code true}.
     */
    static final String EVENT_LOOP_PROPERTY = "micronaut.python.static.event-loop";

    private final Supplier<ExecutorService> ioExecutor;
    private final boolean eventLoop;
    private final PythonEventLoopBeans eventLoopBeans;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator
     * @param ioExecutor The IO executor
     * @param blockingExecutor The blocking executor
     * @param eventLoop Whether a compiled route that never enters Python may stay on the event loop
     * @param eventLoopBeans The beans of such routes created when the application started
     */
    @Inject
    PythonExecutorSelector(
        BeanLocator beanLocator,
        @Named(TaskExecutors.IO) BeanProvider<ExecutorService> ioExecutor,
        @Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> blockingExecutor,
        @Property(name = EVENT_LOOP_PROPERTY, defaultValue = StringUtils.TRUE) boolean eventLoop,
        PythonEventLoopBeans eventLoopBeans) {
        super(beanLocator, ioExecutor, blockingExecutor);
        this.ioExecutor = SupplierUtil.memoized(ioExecutor::get);
        this.eventLoop = eventLoop;
        this.eventLoopBeans = eventLoopBeans;
    }

    @Override
    public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection) {
        // @ExecuteOn names the executor of a Python method as it does for a Java one
        if (isPythonMethod(method) && !Objects.requireNonNull(method).hasStereotype(ExecuteOn.class)) {
            TypeInformation<?> returnType = method.getReturnType();
            if (!returnType.isAsyncOrReactive()) {
                if (eventLoop && method.hasAnnotation(EventLoopSafe.class) && eventLoopBeans.createdAtStartup(method.getDeclaringType())) {
                    // a compiled body that runs no Python runs where a Java method runs, once its bean exists
                    // (created at startup: creating it runs Python); when the default selection offloads it
                    // (to the blocking executor, which may use virtual threads), the IO executor runs it as any
                    // other Python method: a fixed attribute changed by code outside the compilation is read
                    // from the Python object again, which a virtual thread cannot do
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
