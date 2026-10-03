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
package io.micronaut.context.scope;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.annotation.Retention;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Measures filling and closing a custom scope, the work a request scope does for every request. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ScopeTeardownBenchmark {
    private static final Class<?>[] TYPES = {Bean1.class, Bean2.class, Bean3.class, Bean4.class, Bean5.class, Bean6.class, Bean7.class, Bean8.class};

    @Param({"1", "3", "8"})
    int beans;

    private ApplicationContext context;
    private BenchmarkScope scope;
    private Class<?> last;

    @Setup
    public void setup() {
        context = ApplicationContext.run();
        scope = context.getBean(BenchmarkScope.class);
        last = TYPES[beans - 1];
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public Object fillAndClose() {
        // each bean injects the previous one, so resolving the last fills the scope with all of them
        Object bean = context.getBean(last);
        scope.close();
        return bean;
    }

    @Scope
    @Retention(RUNTIME)
    public @interface BenchmarkScoped {
    }

    @Singleton
    public static class BenchmarkScope extends AbstractConcurrentCustomScope<BenchmarkScoped> {
        private final Map<BeanIdentifier, CreatedBean<?>> scoped = new ConcurrentHashMap<>();

        public BenchmarkScope() {
            super(BenchmarkScoped.class);
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            return scoped;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void close() {
            destroyScope(scoped);
        }
    }

    @BenchmarkScoped
    public static class Bean1 {
    }

    @BenchmarkScoped
    public static class Bean2 {
        public Bean2(Bean1 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean3 {
        public Bean3(Bean2 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean4 {
        public Bean4(Bean3 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean5 {
        public Bean5(Bean4 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean6 {
        public Bean6(Bean5 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean7 {
        public Bean7(Bean6 previous) {
        }
    }

    @BenchmarkScoped
    public static class Bean8 {
        public Bean8(Bean7 previous) {
        }
    }
}
