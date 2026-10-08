package io.micronaut.core.util.memo;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@State(Scope.Benchmark)
public class MemoBenchmark {
    private static final MemoizedFlag<AnnotationMetadata> ANNOTATED_FLAG =
        AnnotationMetadata.MEMOIZER_NAMESPACE.newFlag(m -> m.hasAnnotation(Ann1.class));

    @Param({"Bare", "Extra1", "Extra2"})
    String type;
    @Param({"true", "false"})
    boolean annotated;

    private BeanProperty<?, ?> property;

    @Setup
    public void setup() throws Throwable {
        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.getIntrospection(Class.forName(MemoBenchmark.class.getName() + "$" + type));
        property = introspection.getProperty(annotated ? "annotatedField" : "notAnnotatedField").orElseThrow();

        if (memoized() != annotated) {
            throw new AssertionError();
        }
        if (memoizedFallback() != annotated) {
            throw new AssertionError();
        }
        if (direct() != annotated) {
            throw new AssertionError();
        }
    }

    @Benchmark
    public boolean memoized() {
        return ANNOTATED_FLAG.get(property);
    }

    @Benchmark
    public boolean memoizedFallback() {
        // this is the default implementation of get()
        return ANNOTATED_FLAG.compute(property);
    }

    @Benchmark
    public boolean direct() {
        return property.hasAnnotation(Ann1.class);
    }

    public static void main(String[] args) throws Throwable {
        MemoBenchmark memoBenchmark = new MemoBenchmark();
        memoBenchmark.type = "Bare";
        memoBenchmark.annotated = true;
        memoBenchmark.setup();
        memoBenchmark.memoizedFallback();

        Options opt = new OptionsBuilder()
            .include(MemoBenchmark.class.getName() + ".*")
            //.addProfiler(LinuxPerfAsmProfiler.class)
            .build();

        new Runner(opt).run();
    }

    @Introspected
    record Bare(
        @Ann1 String annotatedField,
        String notAnnotatedField
    ) {
    }

    @Introspected
    record Extra1(
        @Ann1 @Ann2 String annotatedField,
        @Ann2 String notAnnotatedField
    ) {
    }

    @Introspected
    record Extra2(
        @Ann1 @Ann2 @Ann3 String annotatedField,
        @Ann2 @Ann3 String notAnnotatedField
    ) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Ann1 {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Ann2 {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Ann3 {
    }
}
