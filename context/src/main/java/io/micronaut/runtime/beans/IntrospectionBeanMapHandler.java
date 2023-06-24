/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.runtime.beans;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMapper;

import java.util.Map;

/**
 * Bean mapper based on introspections.
 *
 * @since 4.1.0
 */
@Internal
public sealed interface IntrospectionBeanMapHandler extends BeanMapHandler permits DefaultIntrospectionBeanMapHandler {
    /**
     * Map the input to the output.
     *
     * @param input       The input
     * @param output      The output
     * @param mapStrategy The map strategy
     * @param left        The input introspection
     * @param right       The output introspection
     * @param <I>         The input generic type
     * @param <O>         The output generic type
     * @return The output
     */
    <I, O> O map(
        @NonNull I input,
        @NonNull O output,
        @NonNull BeanMapper.MapStrategy mapStrategy,
        @NonNull BeanIntrospection<I> left,
        @NonNull BeanIntrospection<O> right
    );

    /**
     * Map the input to the output with the given introspection.
     *
     * @param input               The input
     * @param mapStrategy         The map strategy.
     * @param outputIntrospection The introspection
     * @param <I>                 The input generic
     * @param <O>                 The output generic
     * @return The output.
     */
    <I, O> @NonNull O map(@NonNull I input, @NonNull BeanMapper.MapStrategy mapStrategy, @NonNull BeanIntrospection<O> outputIntrospection);

    /**
     * Map the input to the output with the given introspection.
     *
     * @param input               The input
     * @param mapStrategy         The map strategy.
     * @param inputIntrospection  The input introspection
     * @param outputIntrospection The introspection
     * @param <I>                 The input generic
     * @param <O>                 The output generic
     * @return The output.
     */
    <I, O> @NonNull O map(@NonNull I input, @NonNull BeanMapper.MapStrategy mapStrategy, @NonNull BeanIntrospection<I> inputIntrospection, @NonNull BeanIntrospection<O> outputIntrospection);

    /**
     * Map the input to the output with the given introspection.
     *
     * @param input               The input
     * @param mapStrategy         The map strategy
     * @param outputIntrospection The introspection
     * @param <O>                 The output generic type
     * @return The output
     */
    <O> @NonNull O map(@NonNull Map<String, Object> input, @NonNull BeanMapper.MapStrategy mapStrategy, @NonNull BeanIntrospection<O> outputIntrospection);

    /**
     * Map input to output where input is a map.
     *
     * @param <O>         The output generic type
     * @param input       The input map
     * @param output      The output
     * @param mapStrategy The map strategy
     * @param right       The introspection
     * @return The output
     */
    <O> @NonNull O map(@NonNull Map<String, Object> input, @NonNull O output, @NonNull BeanMapper.MapStrategy mapStrategy, @NonNull BeanIntrospection<O> right);
}
