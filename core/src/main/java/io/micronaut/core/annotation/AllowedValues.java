/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the values a configuration property accepts when its type does not list them, such as a
 * {@code String} option that only accepts {@code "earliest"}, {@code "latest"} or {@code "none"}.
 *
 * <p>The values are written as the {@code enum} keyword of the property in the JSON schema that is
 * generated for a {@code @ConfigurationProperties} type, so that tooling such as the configuration
 * validator of Micronaut JSON Schema reports any other value. For a collection or an array the values
 * apply to its elements, and for a map to its values. For an enum type the values narrow the constants
 * of the enum.</p>
 *
 * <p>Each value is written with the JSON type of the property: the values of a numeric property must be
 * numbers in the range of its Java type, and the values of a {@code boolean} property {@code true} or {@code false}.</p>
 *
 * <p>The annotation is metadata only: the binding of the property does not check the values.</p>
 *
 * @author Graeme Rocher
 * @since 5.2.16
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface AllowedValues {

    /**
     * @return The values the property accepts
     */
    String[] value();
}
