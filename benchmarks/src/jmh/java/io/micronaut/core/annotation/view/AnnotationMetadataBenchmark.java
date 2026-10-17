/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.annotation.view;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.bind.binders.QueryValueArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Annotation metadata paths beyond the single annotation lookups of {@link AnnotationViewBenchmark}: repeatable
 * lookups through a hierarchy, presence checks, metadata construction and a query value binding.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AnnotationMetadataBenchmark {

    private static final Map<String, Map<CharSequence, Object>> ANNOTATIONS = Map.of(ViewAnn.class.getName(), Map.of("value", "x"));

    private ApplicationContext applicationContext;
    private AnnotationMetadata noRepeat;
    private AnnotationMetadata oneRepeat;
    private AnnotationMetadata threeRepeats;
    private AnnotationMetadata queryArgumentMetadata;
    private RequestArgumentBinder<String> queryBinder;
    private ArgumentConversionContext<String> queryContext;
    private HttpRequest<?> request;

    @Setup
    @SuppressWarnings("unchecked")
    public void prepare() {
        applicationContext = ApplicationContext.run();
        noRepeat = applicationContext.getBeanDefinition(ViewBean.class).getRequiredMethod("versioned").getAnnotationMetadata();
        var repeatBean = applicationContext.getBeanDefinition(ViewRepeatBean.class);
        oneRepeat = repeatBean.getRequiredMethod("one").getAnnotationMetadata();
        threeRepeats = repeatBean.getRequiredMethod("three").getAnnotationMetadata();
        ExecutableMethod<ViewBean, Object> query = applicationContext.getBeanDefinition(ViewBean.class).getRequiredMethod("query", String.class);
        Argument<String> argument = (Argument<String>) query.getArguments()[0];
        queryArgumentMetadata = argument.getAnnotationMetadata();
        queryBinder = new QueryValueArgumentBinder<String>(applicationContext.getBean(ConversionService.class)).createSpecific(argument);
        queryContext = ConversionContext.of(argument);
        request = HttpRequest.GET("/query?other=1");
        if (oneRepeat.getAnnotationValuesByType(ViewRepeat.class).size() != 1 || threeRepeats.getAnnotationValuesByType(ViewRepeat.class).size() != 3) {
            throw new IllegalStateException("Unexpected repeatable setup");
        }
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public List<AnnotationValue<ViewRepeat>> repeatableHierarchyNone() {
        return noRepeat.getAnnotationValuesByType(ViewRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<ViewRepeat>> repeatableHierarchyOne() {
        return oneRepeat.getAnnotationValuesByType(ViewRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<ViewRepeat>> repeatableHierarchyThree() {
        return threeRepeats.getAnnotationValuesByType(ViewRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<ViewRepeat>> repeatableByNameHierarchyThree() {
        return threeRepeats.getAnnotationValuesByName(ViewRepeat.class.getName());
    }

    @Benchmark
    public boolean hasAnnotationClass() {
        return queryArgumentMetadata.hasAnnotation(QueryValue.class);
    }

    @Benchmark
    public boolean hasAnnotationName() {
        return queryArgumentMetadata.hasAnnotation(QueryValue.class.getName());
    }

    @Benchmark
    public Object metadataConstruction() {
        return new DefaultAnnotationMetadata(ANNOTATIONS, null, null, ANNOTATIONS, null);
    }

    /**
     * Binds a {@code @QueryValue} argument that is missing from the request, so the default value is looked up.
     */
    @Benchmark
    public ArgumentBinder.BindingResult<String> queryValueMissing() {
        return queryBinder.bind(queryContext, request);
    }
}
