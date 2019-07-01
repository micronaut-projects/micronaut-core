package io.micronaut.core.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.PropertySource;
import io.micronaut.core.annotation.beans.NestedProperties;
import io.micronaut.inject.BeanDefinition;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class NestedAnnotationValueBenchmark {

    ApplicationContext applicationContext;
    BeanDefinition<NestedProperties> introductionBeanDefinition;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        introductionBeanDefinition = applicationContext.getBeanDefinition(NestedProperties.class);
    }
    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public void benchMarkGetValue() {
        introductionBeanDefinition
                .getAnnotation(PropertySource.class)
                .getAnnotations("value");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + NestedAnnotationValueBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }
}
