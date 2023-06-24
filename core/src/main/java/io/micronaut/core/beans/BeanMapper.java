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
package io.micronaut.core.beans;


import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * A bean mapper is an injectable type to map one type to another.
 * @param <I> The input type
 * @param <O> The output type
 * @since 4.1.0
 */
@Experimental
public interface BeanMapper<I, O> {

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param outputType The output type
     * @param mapStrategy The map strategy
     *
     * @return The output
     */
    @NonNull O map(@NonNull I input, @NonNull Class<O> outputType, @NonNull MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param output The output
     * @param mapStrategy The map strategy
     *
     * @return The output
     */
    @NonNull O map(@NonNull I input, @NonNull O output, @NonNull MapStrategy mapStrategy);

    /**
     * Map all the properties from the input to the output. Uses a conflict strategy of {@link MapStrategy.ConflictStrategy#ERROR}.
     *
     * @param input The input
     * @param output The output
     * @return The output
     */
    default @NonNull O map(@NonNull I input, @NonNull O output) {
        return map(input, output, MapStrategy.DEFAULT);
    }

    /**
     * Map all the properties from the input to the output.
     * @param input The input
     * @param outputType The output type
     * @return The output
     */
    default @NonNull O map(@NonNull I input, @NonNull Class<O> outputType) {
        return map(input, outputType, MapStrategy.DEFAULT);
    }

    /**
     * Strategy to use to perform mapping.
     */
    interface MapStrategy {
        /**
         * The default. Uses {@link ConflictStrategy#CONVERT}.
         */
        MapStrategy DEFAULT = new DefaultMapStrategy();

        /**
         * @return The conflict strategy.
         */
        @NonNull ConflictStrategy conflictStrategy();

        /**
         * Default strategy.
         *
         * @param conflictStrategy The conflict strategy
         */
        record DefaultMapStrategy(@NonNull ConflictStrategy conflictStrategy) implements MapStrategy {
            public DefaultMapStrategy() {
                this(ConflictStrategy.CONVERT);
            }
        }

        /**
         * The conflict strategy specifies the behaviour if a conflict is found.
         *
         * <p>A conflict could be if for the example the source input defines a property that doesn't exist in the output or the types don't match</p>
         */
        enum ConflictStrategy {
            /**
             * Ignore the conflict and bind what is possible.
             */
            IGNORE,
            /**
             * Log a warning, but ignore.
             */
            WARN,
            /**
             * Try and convert otherwise error.
             */
            CONVERT,
            /**
             * Throw an {@link IllegalArgumentException}.
             */
            ERROR
        }
    }

}
