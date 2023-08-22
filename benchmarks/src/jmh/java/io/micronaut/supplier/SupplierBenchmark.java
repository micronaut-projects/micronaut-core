package io.micronaut.supplier;

import io.micronaut.core.util.SupplierUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Optional;
import java.util.function.Supplier;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
public class SupplierBenchmark {

    private Supplier<String> memoizedLambda = memoizedUsingLambda(() -> "test");
    private Supplier<String> memoizedNonEmptyUsingLambda = memoizedNonEmptyUsingLambda(() -> "test");
    private Supplier<String> memoized = SupplierUtil.memoized(() -> "test");
    private Supplier<String> memoizedNonEmpty = SupplierUtil.memoizedNonEmpty(() -> "test");

    @Benchmark
    public void memoizedLambda(Blackhole blackhole) {
        blackhole.consume(memoizedLambda.get());
    }

    @Benchmark
    public void memoizedNonEmptyUsingLambda(Blackhole blackhole) {
        blackhole.consume(memoizedNonEmptyUsingLambda.get());
    }

    @Benchmark
    public void memoized(Blackhole blackhole) {
        blackhole.consume(memoized.get());
    }


    @Benchmark
    public void memoizedNonEmpty(Blackhole blackhole) {
        blackhole.consume(memoizedNonEmpty.get());
    }

    private static <T> Supplier<T> memoizedUsingLambda(Supplier<T> actual) {
        return new Supplier<T>() {
            Supplier<T> delegate = this::initialize;
            boolean initialized;

            @Override
            public T get() {
                return delegate.get();
            }

            private synchronized T initialize() {
                if (!initialized) {
                    T value = actual.get();
                    delegate = () -> value;
                    initialized = true;
                }
                return delegate.get();
            }
        };
    }

    private static <T> Supplier<T> memoizedNonEmptyUsingLambda(Supplier<T> actual) {
        return new Supplier<T>() {
            Supplier<T> delegate = this::initialize;
            boolean initialized;

            @Override
            public T get() {
                return delegate.get();
            }

            private synchronized T initialize() {
                if (!initialized) {
                    T value = actual.get();
                    if (value == null) {
                        return null;
                    }
                    if (value instanceof Optional optional && !optional.isPresent()) {
                        return value;
                    }
                    delegate = () -> value;
                    initialized = true;
                }
                return delegate.get();
            }
        };
    }
}
