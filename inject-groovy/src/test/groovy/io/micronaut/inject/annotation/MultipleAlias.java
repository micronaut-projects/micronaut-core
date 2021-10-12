/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.ConfigurationReader;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
public @interface MultipleAlias {
    /**
     * @return The prefix to use to resolve the properties
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return The prefix to use to resolve the properties
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    @AliasFor(member = "value")
    String id() default "";

    /**
     * @return The prefix to use to resolve the properties
     */
    @AliasFor(annotation = Nested.class, member = "num")
    int primitiveAlias() default 0;

    /**
     * @return The prefix to use to resolve the properties
     */
    @AliasFor(annotation = Nested.class, member = "bool")
    boolean bool() default false;
}
