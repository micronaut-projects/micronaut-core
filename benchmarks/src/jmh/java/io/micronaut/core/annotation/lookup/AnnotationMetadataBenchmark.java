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
package io.micronaut.core.annotation.lookup;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Annotation metadata paths beyond the single annotation lookups of {@link AnnotationLookupBenchmark}: repeatable
 * lookups through a hierarchy, presence checks, metadata construction and a query value binding.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AnnotationMetadataBenchmark {

    private static final Map<String, Map<CharSequence, Object>> ANNOTATIONS = Map.of(LookupAnn.class.getName(), Map.of("value", "x"));

    private ApplicationContext applicationContext;
    private AnnotationMetadata typeMetadata;
    private AnnotationMetadata methodMetadata;
    private AnnotationValue<LookupAnn> value1;
    private AnnotationValue<LookupAnn> value2;
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
        noRepeat = applicationContext.getBeanDefinition(LookupBean.class).getRequiredMethod("versioned").getAnnotationMetadata();
        var repeatBean = applicationContext.getBeanDefinition(LookupRepeatBean.class);
        oneRepeat = repeatBean.getRequiredMethod("one").getAnnotationMetadata();
        threeRepeats = repeatBean.getRequiredMethod("three").getAnnotationMetadata();
        ExecutableMethod<LookupBean, Object> query = applicationContext.getBeanDefinition(LookupBean.class).getRequiredMethod("query", String.class);
        Argument<String> argument = (Argument<String>) query.getArguments()[0];
        queryArgumentMetadata = argument.getAnnotationMetadata();
        queryBinder = new QueryValueArgumentBinder<String>(applicationContext.getBean(ConversionService.class)).createSpecific(argument);
        queryContext = ConversionContext.of(argument);
        request = HttpRequest.GET("/query?other=1");
        typeMetadata = applicationContext.getBeanDefinition(LookupBean.class).getAnnotationMetadata();
        methodMetadata = noRepeat;
        // Two distinct values of the same annotation, as qualifier and binding matching compare them
        value1 = typeMetadata.getAnnotation(LookupAnn.class);
        value2 = typeMetadata.getAnnotation(LookupAnn.class);
        if (oneRepeat.getAnnotationValuesByType(LookupRepeat.class).size() != 1 || threeRepeats.getAnnotationValuesByType(LookupRepeat.class).size() != 3) {
            throw new IllegalStateException("Unexpected repeatable setup");
        }
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public List<AnnotationValue<LookupRepeat>> repeatableHierarchyNone() {
        return noRepeat.getAnnotationValuesByType(LookupRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<LookupRepeat>> repeatableHierarchyOne() {
        return oneRepeat.getAnnotationValuesByType(LookupRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<LookupRepeat>> repeatableHierarchyThree() {
        return threeRepeats.getAnnotationValuesByType(LookupRepeat.class);
    }

    @Benchmark
    public List<AnnotationValue<LookupRepeat>> repeatableByNameHierarchyThree() {
        return threeRepeats.getAnnotationValuesByName(LookupRepeat.class.getName());
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

    @Benchmark
    public boolean annotationValueEquals() {
        return value1.equals(value2);
    }

    @Benchmark
    public int annotationValueHashCode() {
        return value1.hashCode();
    }

    /**
     * Compares including the defaults, as qualifiers and interceptor bindings do.
     */
    @Benchmark
    public boolean annotationValueMatches() {
        return value1.matches(value2);
    }

    @Benchmark
    public List<String> hierarchyAnnotationNamesByStereotype() {
        return methodMetadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE);
    }

    @Benchmark
    public List<AnnotationValue<Annotation>> hierarchyAnnotationValuesByStereotype() {
        return methodMetadata.getAnnotationValuesByStereotype(AnnotationUtil.SCOPE);
    }

    @Benchmark
    public AnnotationValue<LookupAnn> hierarchyGetAnnotationByName() {
        return methodMetadata.getAnnotation(LookupAnn.class.getName());
    }

    @Benchmark
    public boolean hierarchyHasAnnotationNameMissing() {
        return methodMetadata.hasAnnotation("io.micronaut.Missing");
    }

    @Benchmark
    public boolean typeHasAnnotationNameMissing() {
        return typeMetadata.hasAnnotation("io.micronaut.Missing");
    }

    @Benchmark
    public String typeArrayMemberStringValue() {
        return typeMetadata.stringValue(LookupArray.class).orElse(null);
    }

}
