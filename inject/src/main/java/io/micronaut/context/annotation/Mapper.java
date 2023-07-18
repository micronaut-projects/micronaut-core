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
import io.micronaut.core.beans.BeanMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
    BeanMapper.MapStrategy.ConflictStrategy conflictStrategy() default BeanMapper.MapStrategy.ConflictStrategy.CONVERT;

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
}
