/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.sourcegen.model.ExpressionDef;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A {@link ParameterDefaultValueProvider} standing in for a language, such as Scala, that compiles
 * a default argument to an accessor the caller invokes. It supports only the marker interface the
 * tests implement, so registering it has no effect on any other parameter.
 */
public final class TestScalaLikeDefaultValueProvider implements ParameterDefaultValueProvider {

    /**
     * Implemented by test parameters that carry a caller-side default.
     */
    public interface ScalaLikeParameter {

        /**
         * @return The default value accessor call, or {@code null} if it cannot be materialised
         */
        @Nullable
        ExpressionDef defaultAccessorCall();
    }

    @Override
    public boolean supports(ParameterElement parameter) {
        return parameter instanceof ScalaLikeParameter;
    }

    @Override
    public Optional<ExpressionDef> defaultValueExpression(ParameterElement parameter, @Nullable ExpressionDef target) {
        return Optional.ofNullable(((ScalaLikeParameter) parameter).defaultAccessorCall());
    }
}
