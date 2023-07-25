package io.micronaut.http.body;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class MessageBodyWriterDesignBenchmark {
    private final List<Itf> itfs = new ArrayList<>();
    private final List<Abs> abs = new ArrayList<>();

    @Setup
    public void prepare() {
        for (int i = 0; i < 50; i++) {
            Abs o = switch (i % 3) {
                case 0 -> new Impl1();
                case 1 -> new Impl2();
                case 2 -> new Impl3();
                default -> throw new AssertionError();
            };
            abs.add(o);
            itfs.add(o);
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(MessageBodyWriterDesignBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void callInterfaces() {
        for (Itf itf : itfs) {
            itf.foo();
        }
    }

    @Benchmark
    public void callAbstract() {
        for (Abs o : abs) {
            o.foo();
        }
    }

    public interface Itf {
        void foo();
    }

    public static abstract class Abs implements Itf {
        @Override
        public abstract void foo();
    }

    public static final class Impl1 extends Abs {
        @Override
        public void foo() {
        }
    }

    public static final class Impl2 extends Abs {
        @Override
        public void foo() {
        }
    }

    public static final class Impl3 extends Abs {
        @Override
        public void foo() {
        }
    }
}
