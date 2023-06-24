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
import io.micronaut.core.beans.BeanMapper;

import java.util.Map;

/**
 * Interface that allows mapping the properties of one bean to another.
 *
 * @since 4.1.0
 */
@Internal
public sealed interface BeanMapHandler permits IntrospectionBeanMapHandler {

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param outputType The output type
     * @param mapStrategy The map strategy
     *
     * @return The output
     * @param <I> The input generic type
     * @param <O> The output generic type
     */
    <I, O> @NonNull O map(@NonNull I input, @NonNull Class<O> outputType, @NonNull BeanMapper.MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param outputType The output type
     * @param mapStrategy The map strategy
     *
     * @return The output
     * @param <O> The output generic type
     */
    <O> @NonNull O map(@NonNull Map<String, Object> input, @NonNull Class<O> outputType, @NonNull BeanMapper.MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param output The output
     * @param mapStrategy The map strategy
     *
     * @return The output
     * @param <I> The input generic type
     * @param <O> The output generic type
     */
    <I, O> @NonNull O map(@NonNull I input, @NonNull O output, @NonNull BeanMapper.MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param output The output
     * @param mapStrategy The conflict strategy
     *
     * @return The output
     * @param <O> The output generic type
     */
    <O> @NonNull O map(@NonNull Map<String, Object> input, @NonNull O output, @NonNull BeanMapper.MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output. Uses a map strategy of {@link BeanMapper.MapStrategy#DEFAULT}.
     *
     * @param input The input
     * @param output The output
     * @return The output
     * @param <I> The input generic type
     * @param <O> The output generic type
     */
    default <I, O> @NonNull O map(@NonNull I input, O output) {
        return map(input, output, BeanMapper.MapStrategy.DEFAULT);
    }

    /**
     * Map all the properties from the input to the output. Uses a map strategy of {@link BeanMapper.MapStrategy#DEFAULT}.
     * @param input The input
     * @param outputType The output type
     * @return The output
     * @param <I> The input generic type
     * @param <O> The output generic type
     */
    default <I, O> O map(I input, Class<O> outputType) {
        return map(input, outputType, BeanMapper.MapStrategy.DEFAULT);
    }

}
