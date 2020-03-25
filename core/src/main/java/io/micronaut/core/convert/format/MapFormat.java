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
package io.micronaut.core.convert.format;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.core.naming.conventions.StringConvention;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows configuration how map property values are injected.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface MapFormat {

    /**
     * @return The map transformation to apply
     */
    MapTransformation transformation() default MapTransformation.NESTED;

    /**
     * @return The key format to apply to keys
     */
    StringConvention keyFormat() default StringConvention.RAW;


    /**
     * Specifies the type of the map that should be injected.
     */
    enum MapTransformation {

        /**
         * A nested map has the any keys such as {@code foo.bar} transformed into a structure that is a map of maps
         * such as JSON.
         */
        NESTED,

        /**
         * A flat map has the keys flattened such that {@code foo.bar} is a single map.
         */
        FLAT
    }
}
