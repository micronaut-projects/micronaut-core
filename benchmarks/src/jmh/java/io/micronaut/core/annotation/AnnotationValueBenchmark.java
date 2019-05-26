package io.micronaut.core.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.beans.TestIntroduction;
import io.micronaut.core.convert.ConversionServiceBenchmark;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class AnnotationValueBenchmark {

    ApplicationContext applicationContext;
    BeanDefinition<TestIntroduction> introductionBeanDefinition;
    private ExecutableMethod<TestIntroduction, Object> testIntroductionMethod;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        introductionBeanDefinition = applicationContext.getBeanDefinition(TestIntroduction.class);
        testIntroductionMethod = introductionBeanDefinition.getRequiredMethod("testMethod");
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public void benchMarkGetValue() {
        testIntroductionMethod.getValue(Produces.class, String.class);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + AnnotationValueBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }
}
