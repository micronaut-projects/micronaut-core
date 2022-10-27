/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Configures logback for runtime reflection if required.
 *
 * @since 4.0.0
 * @author graemerocher
 */
@AutomaticFeature
@Internal
public final class LoggingFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Stream.of("ch.qos.logback.classic.encoder.PatternLayoutEncoder",
                "ch.qos.logback.classic.pattern.DateConverter",
                "ch.qos.logback.classic.pattern.LevelConverter",
                "ch.qos.logback.classic.pattern.LineSeparatorConverter",
                "ch.qos.logback.classic.pattern.LoggerConverter",
                "ch.qos.logback.classic.pattern.MessageConverter",
                "ch.qos.logback.classic.pattern.ThreadConverter",
                "ch.qos.logback.classic.pattern.color.HighlightingCompositeConverter",
                "ch.qos.logback.core.ConsoleAppender",
                "ch.qos.logback.core.OutputStreamAppender",
                "ch.qos.logback.core.encoder.LayoutWrappingEncoder",
                "ch.qos.logback.core.pattern.PatternLayoutEncoderBase",
                "ch.qos.logback.core.pattern.color.CyanCompositeConverter",
                "ch.qos.logback.core.pattern.color.GrayCompositeConverter",
                "ch.qos.logback.core.pattern.color.MagentaCompositeConverter")
            .map(access::findClassByName)
            .filter(Objects::nonNull)
            .forEach(t -> {
                RuntimeReflection.registerForReflectiveInstantiation(t);
                RuntimeReflection.register(t);
                Constructor<?>[] declaredConstructors = t.getConstructors();
                for (Constructor<?> c : declaredConstructors) {
                    RuntimeReflection.register(c);
                }
                Method[] methods = t.getMethods();
                for (Method method : methods) {
                    RuntimeReflection.register(method);
                }
            });
    }
}
