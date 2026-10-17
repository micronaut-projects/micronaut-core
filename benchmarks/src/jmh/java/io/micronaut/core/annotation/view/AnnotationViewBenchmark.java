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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationView;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
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

import java.util.concurrent.TimeUnit;

/**
 * Compares reading a member through {@code getAnnotation(...)} with reading it through the
 * {@link AnnotationView}. The {@code version*} pair is the expression of {@code VersionClientRequestBinder#bind}
 * before and after it switched to the view.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AnnotationViewBenchmark {

    private ApplicationContext applicationContext;
    private BeanDefinition<ViewBean> definition;
    private ExecutableMethod<ViewBean, Object> method;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        definition = applicationContext.getBeanDefinition(ViewBean.class);
        method = definition.getRequiredMethod("versioned");
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public String typeGetAnnotation() {
        AnnotationValue<ViewAnn> value = definition.getAnnotation(ViewAnn.class);
        return value != null ? value.stringValue("value").orElse(null) : null;
    }

    /**
     * Reads a member through the conversion service, which uses the convertible values of the annotation value.
     */
    @Benchmark
    public Integer typeGetAnnotationConverted() {
        AnnotationValue<ViewAnn> value = definition.getAnnotation(ViewAnn.class);
        return value != null ? value.get("count", Integer.class).orElse(null) : null;
    }

    @Benchmark
    public String typeView() {
        AnnotationView view = definition.getAnnotationView(ViewAnn.class);
        return view != null ? view.stringValue("value").orElse(null) : null;
    }

    @Benchmark
    public int typeGetAnnotationTwoMembers() {
        AnnotationValue<ViewAnn> value = definition.getAnnotation(ViewAnn.class);
        return value != null ? value.stringValue("value").orElse("").length() + value.intValue("count").orElse(0) : 0;
    }

    @Benchmark
    public int typeViewTwoMembers() {
        AnnotationView view = definition.getAnnotationView(ViewAnn.class);
        return view != null ? view.stringValue("value").orElse("").length() + view.intValue("count").orElse(0) : 0;
    }

    /**
     * The method metadata is a hierarchy of the method and the type: the annotation is on the type only.
     */
    @Benchmark
    public String hierarchyGetAnnotation() {
        AnnotationValue<ViewAnn> value = method.getAnnotation(ViewAnn.class);
        return value != null ? value.stringValue("value").orElse(null) : null;
    }

    @Benchmark
    public String hierarchyView() {
        AnnotationView view = method.getAnnotationView(ViewAnn.class);
        return view != null ? view.stringValue("value").orElse(null) : null;
    }

    @Benchmark
    public String versionBinderBefore() {
        return method.findAnnotation(Version.class)
            .flatMap(AnnotationValue::stringValue)
            .filter(StringUtils::isNotEmpty)
            .orElse(null);
    }

    @Benchmark
    public String versionBinderAfter() {
        AnnotationView versionAnnotation = method.getAnnotationView(Version.class);
        if (versionAnnotation == null) {
            return null;
        }
        String version = versionAnnotation.stringValue().orElse(null);
        return StringUtils.isEmpty(version) ? null : version;
    }
}
