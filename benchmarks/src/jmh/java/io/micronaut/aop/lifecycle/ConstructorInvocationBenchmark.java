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
package io.micronaut.aop.lifecycle;

import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
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

/** Measures complete prototype creation and destruction with constructor advice. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
public class ConstructorInvocationBenchmark {
    @State(Scope.Thread)
    public static class ContextState {
        BeanContext context;

        @Setup
        public void setup() {
            context = ApplicationContext.run();
        }

        @TearDown
        public void close() {
            context.close();
        }
    }

    @Benchmark
    public ConstructedBean createAndDestroy(ContextState state) {
        BeanRegistration<ConstructedBean> registration = state.context.getBeanRegistration(ConstructedBean.class, null);
        state.context.destroyBean(registration);
        return registration.bean();
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
    public @interface Tracked { }

    @Prototype
    @Tracked
    public static class ConstructedBean { }

    @Singleton
    @InterceptorBean(Tracked.class)
    public static class Advice implements ConstructorInterceptor<ConstructedBean> {
        @Override
        public ConstructedBean intercept(ConstructorInvocationContext<ConstructedBean> context) {
            return context.proceed();
        }
    }
}
