/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
