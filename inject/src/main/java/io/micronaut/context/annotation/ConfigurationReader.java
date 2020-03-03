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
package io.micronaut.context.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>A meta annotation for use with other annotations to indicate that the annotation reads configuration.</p>
 *
 * @author Graeme Rocher
 * @see ConfigurationProperties
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ConfigurationReader {

    /**
     * The prefix to use when resolving properties. The prefix should be defined in kebab case. Example: my-app.foo.
     *
     * @return The configuration entry to read
     */
    String value() default "";

    /**
     * @return The prefix to use
     */
    String prefix() default "";

    /**
     * @return The names of the properties to include
     */
    String[] includes() default {};

    /**
     * @return The names of the properties to exclude
     */
    String[] excludes() default {};
}
