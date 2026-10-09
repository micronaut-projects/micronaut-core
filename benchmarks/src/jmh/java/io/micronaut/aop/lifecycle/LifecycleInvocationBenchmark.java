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

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Measures lifecycle dispatch separately from complete bean ownership and first use in a context. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
public class LifecycleInvocationBenchmark {
    @State(Scope.Thread)
    public static class InvocationState {
        @Param({"0", "1", "4"})
        public int candidates;
        @Param({"POST_CONSTRUCT", "PRE_DESTROY"})
        public InterceptorKind kind;
        BeanContext context;
        BeanDefinition<LifecycleBean> definition;
        DefaultBeanResolutionContext resolution;
        ExecutableMethod<LifecycleBean, LifecycleBean> method;
        LifecycleBean bean;

        @Setup
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void setup() {
            context = ApplicationContext.run();
            definition = context.getBeanDefinition(LifecycleBean.class);
            bean = new LifecycleBean();
            method = (ExecutableMethod) definition.getRequiredMethod("event");
            List<?> available = new ArrayList<>(context.getBeanRegistrations(
                Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(definition.getAnnotationMetadata())));
            if (available.size() != 4) {
                throw new IllegalStateException("Expected four matching benchmark interceptors: " + available.size());
            }
            resolution = new DefaultBeanResolutionContext(context, definition);
            resolution.setBeanInterceptors(definition, available.subList(0, candidates));
        }

        @TearDown
        public void close() {
            resolution.close();
            context.close();
        }
    }

    @State(Scope.Thread)
    public static class ContextState {
        BeanContext context;
        @Setup public void setup() { context = ApplicationContext.run(); }
        @TearDown public void close() { context.close(); }
    }

    @Benchmark
    public LifecycleBean retainedCandidates(InvocationState state) {
        return state.kind == InterceptorKind.POST_CONSTRUCT
            ? MethodInterceptorChain.initialize(state.resolution, state.context, state.definition, state.method, state.bean)
            : MethodInterceptorChain.dispose(state.resolution, state.context, state.definition, state.method, state.bean);
    }

    @Benchmark
    public LifecycleBean createAndDestroy(ContextState state) {
        BeanRegistration<LifecycleBean> registration = state.context.getBeanRegistration(LifecycleBean.class, null);
        state.context.destroyBean(registration);
        return registration.bean();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public LifecycleBean firstUse() {
        try (BeanContext context = ApplicationContext.run()) {
            BeanRegistration<LifecycleBean> registration = context.getBeanRegistration(LifecycleBean.class, null);
            context.destroyBean(registration);
            return registration.bean();
        }
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    @InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
    @InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
    public @interface Tracked { }

    @Prototype
    @Tracked
    public static class LifecycleBean {
        long calls;
        @Executable public LifecycleBean event() { calls++; return this; }
        @PostConstruct void initialize() { calls++; }
        @PreDestroy void destroy() { calls++; }
    }

    public abstract static class Advice implements MethodInterceptor<Object, Object> {
        @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    }
    @Singleton @InterceptorBean(Tracked.class) public static class FirstAdvice extends Advice { }
    @Singleton @InterceptorBean(Tracked.class) public static class SecondAdvice extends Advice { }
    @Singleton @InterceptorBean(Tracked.class) public static class ThirdAdvice extends Advice { }
    @Singleton @InterceptorBean(Tracked.class) public static class FourthAdvice extends Advice { }
}
