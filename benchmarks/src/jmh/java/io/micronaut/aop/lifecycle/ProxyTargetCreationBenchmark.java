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
import io.micronaut.context.annotation.Prototype;
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

/** Measures creating a proxy: one that is its own target, and those that front a separate target. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class ProxyTargetCreationBenchmark {
    private ApplicationContext context;

    @Setup
    public void setup() {
        context = ApplicationContext.run();
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public Object subclass() {
        return context.getBean(SubclassBean.class);
    }

    @Benchmark
    public Object fixed() {
        return context.getBean(FixedBean.class);
    }

    @Benchmark
    public Object lazy() {
        return context.getBean(LazyBean.class);
    }

    @Benchmark
    public Object lazyPerTarget() {
        return context.getBean(LazyPerTargetBean.class);
    }

    @Around
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface Created {
    }

    @Singleton
    @InterceptorBean(Created.class)
    public static class CreatedInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Prototype
    @Created
    public static class SubclassBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Prototype
    @Created
    @Around(proxyTarget = true)
    public static class FixedBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Prototype
    @Created
    @Around(proxyTarget = true, lazy = true)
    public static class LazyBean {
        public int increment(int value) {
            return value + 1;
        }
    }

    @Prototype
    @Created
    @Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)
    public static class LazyPerTargetBean {
        public int increment(int value) {
            return value + 1;
        }
    }
}
