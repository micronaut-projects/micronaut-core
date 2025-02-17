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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that can be used on abstract methods for mapping, merging multiple objects.
 * The method needs to define a single return type and at least one argument.
 *
 * <p>Inspired by similar frameworks like MapStruct but internally uses the {@link io.micronaut.core.beans.BeanIntrospection} model.</p>
 *
 * @author Graeme Rocher
 * @since 4.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Executable(processOnStartup = true)
public @interface Mapper {

    /**
     * A merge strategy to supply to {@link #mergeStrategy()}.
     * Properties from subsequent arguments will always override properties from previous arguments with the same name.
     */
    String MERGE_STRATEGY_ALWAYS_OVERRIDE = "ALWAYS_OVERRIDE";

    /**
     * A merge strategy to supply to {@link #mergeStrategy()}.
     * Non-null properties from subsequent arguments will override properties from previous arguments.
     */
    String MERGE_STRATEGY_NOT_NULL_OVERRIDE = "NOT_NULL_OVERRIDE";

    /**
     * @return Defined mappings.
     */
    Mapping[] value() default {};

    /**
     * @return The conflict strategy.
     */
    ConflictStrategy conflictStrategy() default ConflictStrategy.CONVERT;

    /**
     * The merge strategy to use if method has more than one parameter.
     *
     * <p>By default, any non-null property from subsequent arguments will
     * override property with the same name from previous arguments.</p>
     *
     * <p>To define a custom strategy, create a named singleton of type {@link MergeStrategy}
     * and specify the same name here.</p>
     *
     * @return The merge strategy
     */
    String mergeStrategy() default MERGE_STRATEGY_NOT_NULL_OVERRIDE;

    /**
     * The mappings.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.TYPE })
    @Repeatable(value = Mapper.class)
    @interface Mapping {
        String MEMBER_TO = "to";
        String MEMBER_FROM = "from";
        String MEMBER_CONDITION = "condition";
        String MEMBER_FORMAT = "format";
        String MEMBER_DEFAULT_VALUE = "defaultValue";

        /**
         * The property name to map to. When not specified assume the root bean is being mapped to.
         *
         * @return name of the property to map to.
         */
        String to() default "";

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

        /**
         * @return The format to convert numbers or dates into a string.
         */
        String format() default "";
    }

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
     * An interface for defining merge strategies.
     * Merge strategies are used when the mapping has two or more arguments.
     */
    interface MergeStrategy {

        /**
         * Merge a single property by returning the value to set the property to.
         * The merge strategy will be called every time a property needs to be set, even for properties of the first argument.
         *
         * <p>The property that is returned from the method will be set. For example, if you do not want
         * to update the property, return the {@code currentValue}.</p>
         *
         * @param currentValue The currently specified value
         * @param value The newly supplied value
         * @param valueOwner The owner of the new value
         * @param propertyName The name of the property to merge
         * @param mappedPropertyName The name of the property from the owner
         * @return The new value to set
         */
        @Nullable
        Object merge(@Nullable Object currentValue, @Nullable Object value, @NonNull Object valueOwner, @NonNull String propertyName, @NonNull String mappedPropertyName);

    }
}
