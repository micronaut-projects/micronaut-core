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

/** Measures an intercepted method call through a generated proxy, with and without arguments. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class MethodInvocationBenchmark {
    private ApplicationContext context;
    private InvokedBean bean;

    @Setup
    public void setup() {
        context = ApplicationContext.run();
        bean = context.getBean(InvokedBean.class);
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public int noArguments() {
        return bean.answer();
    }

    @Benchmark
    public int oneArgument() {
        return bean.increment(41);
    }

    @Around
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface Invoked {
    }

    @Singleton
    @InterceptorBean(Invoked.class)
    public static class InvokedInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Singleton
    @Invoked
    public static class InvokedBean {
        public int answer() {
            return 42;
        }

        public int increment(int value) {
            return value + 1;
        }
    }
}
