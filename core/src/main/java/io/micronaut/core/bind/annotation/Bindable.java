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
package io.micronaut.core.bind.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * An annotation stereotype for other annotations that indicates a method {@link io.micronaut.core.type.Argument} is bindable.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.METHOD})
public @interface Bindable {

    /**
     * The name of the annotation.
     */
    String NAME = Bindable.class.getName();

    /**
     * @return The name of the bindable source
     */
    String value() default "";

    /**
     * The default value to use if no bindable value is present.
     *
     * @return The default value
     */
    String defaultValue() default "";

}
