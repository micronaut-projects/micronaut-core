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

import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonAppend;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.annotation.JsonSerialize;

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
            PropertyNamingStrategies.LowerDotCaseStrategy.class
        ).forEach(RuntimeReflection::registerForReflectiveInstantiation);

        try {
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonDeserialize.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonSerialize.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonSerialize.Typing.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonAppend.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonAppend.Attr.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonAppend.Prop.class);
            RuntimeReflection.register(com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.class);
            RuntimeProxyCreation.register(JsonDeserialize.class);
            RuntimeProxyCreation.register(JsonSerialize.class);
            RuntimeProxyCreation.register(JsonAppend.class);
            RuntimeProxyCreation.register(JsonAppend.Attr.class);
            RuntimeProxyCreation.register(JsonAppend.Prop.class);
            RuntimeProxyCreation.register(JsonPOJOBuilder.class);
        } catch (LinkageError ignored) {
        }
    }
}
