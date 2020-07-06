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
package io.micronaut.tracing.instrument.hystrix;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.tracing.instrument.util.TracingInvocationInstrumenterFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Replaces the default {@link HystrixConcurrencyStrategy} with one that is enhanced for Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = HystrixConcurrencyStrategy.class)
@Requires(beans = TracingInvocationInstrumenterFactory.class)
@Singleton
@Internal
public class TracingHystrixConcurrentStrategy extends HystrixConcurrencyStrategy {

    private final HystrixConcurrencyStrategy delegate;
    private final TracingInvocationInstrumenterFactory tracingInvocationInstrumenterFactory;

    /**
     * Creates enhanced {@link HystrixConcurrencyStrategy} for tracing.
     *
     * @param tracingInvocationInstrumenterFactory For instrumenting callable
     * @param hystrixConcurrencyStrategy           Different behavior or implementations for concurrency related aspects of the system with default implementations
     */
    @Inject
    public TracingHystrixConcurrentStrategy(TracingInvocationInstrumenterFactory tracingInvocationInstrumenterFactory,
                                            @Nullable HystrixConcurrencyStrategy hystrixConcurrencyStrategy) {
        this.delegate = hystrixConcurrencyStrategy != null ? hystrixConcurrencyStrategy : HystrixConcurrencyStrategyDefault.getInstance();
        this.tracingInvocationInstrumenterFactory = tracingInvocationInstrumenterFactory;
    }

    @Override
    public ThreadPoolExecutor getThreadPool(
            HystrixThreadPoolKey threadPoolKey,
            HystrixProperty<Integer> corePoolSize,
            HystrixProperty<Integer> maximumPoolSize,
            HystrixProperty<Integer> keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        return delegate.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public ThreadPoolExecutor getThreadPool(
            HystrixThreadPoolKey threadPoolKey,
            HystrixThreadPoolProperties threadPoolProperties) {
        return delegate.getThreadPool(threadPoolKey, threadPoolProperties);
    }

    @Override
    public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
        return delegate.getBlockingQueue(maxQueueSize);
    }

    @Override
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        Callable<T> wrapped = super.wrapCallable(callable);
        final InvocationInstrumenter instrumenter = tracingInvocationInstrumenterFactory.newTracingInvocationInstrumenter();
        if (instrumenter != null) {
            return InvocationInstrumenter.instrument(wrapped, instrumenter);
        } else {
            return wrapped;
        }
    }

    @Override
    public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
        return delegate.getRequestVariable(rv);
    }

}
