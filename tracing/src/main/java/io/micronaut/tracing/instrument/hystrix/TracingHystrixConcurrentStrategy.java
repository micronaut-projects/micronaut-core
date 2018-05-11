/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.tracing.instrument.hystrix;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.instrument.util.TracingCallable;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;

import javax.annotation.Nullable;
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
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
@Singleton
public class TracingHystrixConcurrentStrategy extends HystrixConcurrencyStrategy {

    private final HystrixConcurrencyStrategy delegate;
    private final Tracer tracer;

    /**
     * Creates enhanced {@link HystrixConcurrencyStrategy} for tracing.
     *
     * @param tracer For span creation and propagation across arbitrary transports
     * @param hystrixConcurrencyStrategy Different behavior or implementations for concurrency related aspects of the system with default implementations
     */
    @Inject
    public TracingHystrixConcurrentStrategy(Tracer tracer, @Nullable HystrixConcurrencyStrategy hystrixConcurrencyStrategy) {
        this.delegate = hystrixConcurrencyStrategy != null ? hystrixConcurrencyStrategy : HystrixConcurrencyStrategyDefault.getInstance();
        this.tracer = tracer;
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
        if (callable instanceof TracingCallable) {
            return callable;
        } else {
            return new TracingCallable<>(wrapped, tracer);
        }
    }

    @Override
    public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
        return delegate.getRequestVariable(rv);
    }
}
