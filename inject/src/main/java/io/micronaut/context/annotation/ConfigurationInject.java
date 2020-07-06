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
package io.micronaut.context.annotation;

import io.micronaut.core.annotation.Creator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Allows injecting configuration values into a constructor or method based
 * on the parameter names.
 *
 * <p>By default inherits the configuration prefix from any {@link ConfigurationProperties} or {@link EachProperty} definitions present at the class level.</p>
 *
 * <p>An additional prefix can be attached using the {@link #value()} member.</p>
 *
 * @author Graeme Rocher
 * @since 1.3.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Creator
public @interface ConfigurationInject {
    /**
     * @return THe configuration prefix to use.
     */
    String value() default "";
}
