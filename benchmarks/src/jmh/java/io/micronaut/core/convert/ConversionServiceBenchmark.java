package io.micronaut.core.convert;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.net.URI;

@State(Scope.Benchmark)
public class ConversionServiceBenchmark {

    ConversionService conversionService;

    @Setup
    public void prepare() {
        conversionService = ConversionService.SHARED;
    }

    @Benchmark
    public void convertCacheHit() {
        conversionService.convert("10", Integer.class);
    }

    @Benchmark
    public void convertCacheMiss() {
        conversionService.convert(URI.create("http://test.com"), Integer.class);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + ConversionServiceBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }
}
