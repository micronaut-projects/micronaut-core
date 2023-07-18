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
package io.micronaut.context.annotation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An annotation that can be used on abstract methods that define a return type and exactly a single argument.
 *
 * <p>Inspired by similar frameworks like MapStruct but internally uses the {@link io.micronaut.core.beans.BeanIntrospection} model.</p>
 *
 * @author Graeme Rocher
 * @since 4.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Experimental
public @interface Mapper {

    /**
     * @return Defined mappings.
     */
    Mapping[] value() default {};

    /**
     * @return The conflict strategy.
     */
    MapStrategy.ConflictStrategy conflictStrategy() default MapStrategy.ConflictStrategy.CONVERT;

    /**
     * The mappings.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(value = Mapper.class)
    @interface Mapping {
        String MEMBER_TO = "to";
        String MEMBER_FROM = "from";

        /**
         * @return name of the property to map to.
         */
        String to();

        /**
         * Specifies the name of the property to map from. Can be an expression.
         * @return Name of the property to map from.
         */
        String from() default "";

        /**
         * @return An expression the evaluates to true if the mapping should apply.
         */
        String condition() default "";

        /**
         * @return The default value to use.
         */
        String defaultValue() default "";
    }

    /**
     * Strategy to use to perform mapping.
     */
    sealed interface MapStrategy permits DefaultMapStrategy {
        /**
         * The default. Uses {@link ConflictStrategy#CONVERT}.
         */
        MapStrategy DEFAULT = new DefaultMapStrategy();

        /**
         * @return The conflict strategy.
         */
        @NonNull ConflictStrategy conflictStrategy();

        Map<String, BiFunction<MapStrategy, Object, Object>> customMappers();

        /**
         * The conflict strategy specifies the behaviour if a conflict is found.
         *
         * <p>A conflict could be if for the example the source input defines a property that doesn't exist in the output or the types don't match</p>
         */
        enum ConflictStrategy {
            /**
             * Try and convert otherwise error.
             */
            CONVERT,
            /**
             * Throw an {@link IllegalArgumentException}.
             */
            ERROR
        }

        /**
         * @return A map strategy builder.
         */
        static @NonNull Builder builder() {
            return new Builder();
        }

        /**
         * Builder for constructing {@link MapStrategy}.
         */
        @Experimental
        final class Builder {
            private Map<String, BiFunction<MapStrategy,  Object, Object>> customMappers = new HashMap<>();
            private ConflictStrategy conflictStrategy = ConflictStrategy.CONVERT;

            /**
             * @param conflictStrategy The conflict strategy
             * @return This builder
             */
            public @NonNull Builder withConflictStrategy(ConflictStrategy conflictStrategy) {
                this.conflictStrategy = conflictStrategy;
                return this;
            }

            /**
             * @param name The name of the target property
             * @param mapper The mapper
             * @return This builder
             */
            public @NonNull Builder withCustomMapper(@NonNull String name, @NonNull BiFunction<MapStrategy, Object, Object> mapper) {
                customMappers.put(name, mapper);
                return this;
            }

            /**
             * @return Build the map strategy.
             */
            public @NonNull MapStrategy build() {
                if (CollectionUtils.isEmpty(customMappers) && conflictStrategy == ConflictStrategy.CONVERT) {
                    return MapStrategy.DEFAULT;
                }
                return new DefaultMapStrategy(conflictStrategy, customMappers);
            }
        }
    }
}
