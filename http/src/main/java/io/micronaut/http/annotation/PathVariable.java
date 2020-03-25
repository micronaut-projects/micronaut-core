/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used to bind a parameter exclusively from a path variable.
 *
 * @author graemerocher
 * @since 1.0.3
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Bindable
public @interface PathVariable {
    /**
     * @return The name of the parameter
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    @AliasFor(member = "name")
    String value() default "";

    /**
     * @return The name of the parameter
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    @AliasFor(member = "value")
    String name() default "";

    /**
     * @see Bindable#defaultValue()
     * @return The default value
     */
    @AliasFor(annotation = Bindable.class, member = "defaultValue")
    String defaultValue() default "";
}
