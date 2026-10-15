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
package io.micronaut.aop.lifecycle;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Measures an intercepted method call through each kind of proxy that fronts a separate target. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class ProxyTargetInvocationBenchmark {
    private ApplicationContext context;
    private FixedBean fixed;
    private LazyBean lazy;
    private LazyPerTargetBean lazyPerTarget;
    private CachedBean cached;
    private CachedPerTargetBean cachedPerTarget;
    private HotSwapBean hotSwap;

    @Setup
    public void setup() {
        context = ApplicationContext.run();
        fixed = context.getBean(FixedBean.class);
        lazy = context.getBean(LazyBean.class);
        lazyPerTarget = context.getBean(LazyPerTargetBean.class);
        cached = context.getBean(CachedBean.class);
        cachedPerTarget = context.getBean(CachedPerTargetBean.class);
        hotSwap = context.getBean(HotSwapBean.class);
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public int fixed() {
        return fixed.increment(41);
    }

    @Benchmark
    public int lazy() {
        return lazy.increment(41);
    }

    @Benchmark
    public int lazyPerTarget() {
        return lazyPerTarget.increment(41);
    }

    @Benchmark
    public int cached() {
        return cached.increment(41);
    }

    @Benchmark
    public int cachedPerTarget() {
        return cachedPerTarget.increment(41);
    }

    @Benchmark
    public int hotSwap() {
        return hotSwap.increment(41);
    }

    @Around
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface Traced {
    }

    @Singleton
    @InterceptorBean(Traced.class)
    public static class TracedInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true)
    public static class FixedBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true, lazy = true)
    public static class LazyBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)
    public static class LazyPerTargetBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)
    public static class CachedBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true, lazyInterceptorsPerTarget = true)
    public static class CachedPerTargetBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Singleton
    @Traced
    @Around(proxyTarget = true, hotswap = true)
    public static class HotSwapBean {
        public int increment(int value) {
            return value + 1;
        }
    }
}
