package io.micronaut.core.beans;

import io.micronaut.core.annotation.Introspected;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
public class PropertyIndexBenchmark {
    @Param({"1", "2", "3"})
    int typeCount = 3;
    @Param({"50"})
    int itemCount = 100;

    String needle;
    BeanIntrospection<?>[] introspections;

    public static void main(String[] args) throws RunnerException {
        PropertyIndexBenchmark propertyIndexBenchmark = new PropertyIndexBenchmark();
        propertyIndexBenchmark.setUp();
        // calm down shipilev, I'm only verifying the benchmark works.
        propertyIndexBenchmark.test(new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous."));

        Options opt = new OptionsBuilder()
            .include(PropertyIndexBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Setup
    public void setUp() {
        introspections = IntStream.range(0, itemCount)
            .mapToObj(i -> switch (ThreadLocalRandom.current().nextInt(typeCount)) {
                case 0 -> BeanA.class;
                case 1 -> BeanB.class;
                case 2 -> BeanC.class;
                default -> throw new AssertionError();
            })
            .map(BeanIntrospector.SHARED::getIntrospection)
            .toArray(BeanIntrospection[]::new);
        needle = "foo";
    }

    @Benchmark
    public void test(Blackhole blackhole) {
        String needle = this.needle;
        for (BeanIntrospection<?> introspection : introspections) {
            blackhole.consume(introspection.propertyIndexOf(needle));
        }
    }

    @Introspected
    public record BeanA(
        String foo,
        String bar
    ) {
    }

    @Introspected
    public record BeanB(
        String baz,
        int foo
    ) {
    }

    @Introspected
    public record BeanC(
        String fizz,
        double buzz,
        int foo
    ) {
    }
}
