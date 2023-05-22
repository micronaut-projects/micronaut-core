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
package io.micronaut.jackson;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.util.stream.Stream;

/**
 * A native image feature that configures the jackson-databind library.
 *
 * @author Jonas Konrad
 * @since 3.4.1
 */
@Internal
final class JacksonDatabindFeature implements Feature {
    @SuppressWarnings("deprecation")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Stream.of(
                PropertyNamingStrategies.LowerCamelCaseStrategy.class,
                PropertyNamingStrategies.UpperCamelCaseStrategy.class,
                PropertyNamingStrategies.SnakeCaseStrategy.class,
                PropertyNamingStrategies.UpperSnakeCaseStrategy.class,
                PropertyNamingStrategies.LowerCaseStrategy.class,
                PropertyNamingStrategies.KebabCaseStrategy.class,
                PropertyNamingStrategies.LowerDotCaseStrategy.class,

                PropertyNamingStrategy.UpperCamelCaseStrategy.class,
                PropertyNamingStrategy.SnakeCaseStrategy.class,
                PropertyNamingStrategy.LowerCaseStrategy.class,
                PropertyNamingStrategy.KebabCaseStrategy.class,
                PropertyNamingStrategy.LowerDotCaseStrategy.class
        ).forEach(RuntimeReflection::registerForReflectiveInstantiation);
    }
}
