package io.micronaut.context.scope;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.context.scope.ThreadLocal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class ThreadLocalScopeBenchmark {
    ApplicationContext ctx;
    Holder holder;

    @Setup
    public void setup() {
        ctx = ApplicationContext.run(Map.of("spec.name", "ThreadLocalScopeBenchmark"));
        holder = ctx.getBean(Holder.class);
    }

    @TearDown
    public void tearDown() {
        ctx.close();
    }

    @Benchmark
    public int bench() {
        return holder.myThreadLocal.foo();
    }

    public static void main(String[] args) throws RunnerException {
        if (false) {
            ThreadLocalScopeBenchmark b = new ThreadLocalScopeBenchmark();
            b.setup();
            for (int i = 0; i < 100; i++) {
                b.bench();
            }
            return;
        }

        Options opt = new OptionsBuilder()
            .include(ThreadLocalScopeBenchmark.class.getName() + ".*")
            .warmupIterations(10)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();
        new Runner(opt).run();
    }

    @Singleton
    @Requires(property = "spec.name", value = "ThreadLocalScopeBenchmark")
    static class Holder {
        @Inject
        MyThreadLocal myThreadLocal;
    }

    @ThreadLocal
    static class MyThreadLocal {
        private final int foo = ThreadLocalRandom.current().nextInt();

        int foo() {
            return foo;
        }
    }
}
