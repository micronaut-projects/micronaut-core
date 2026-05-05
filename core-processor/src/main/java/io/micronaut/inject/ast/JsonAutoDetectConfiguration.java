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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Internal metadata mapped from Jackson's {@code JsonAutoDetect}.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
@Documented
@Retention(CLASS)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface JsonAutoDetectConfiguration {

    String MEMBER_GETTER_VISIBILITY = "getterVisibility";
    String MEMBER_IS_GETTER_VISIBILITY = "isGetterVisibility";
    String MEMBER_SETTER_VISIBILITY = "setterVisibility";
    String MEMBER_FIELD_VISIBILITY = "fieldVisibility";

    /**
     * Returns the getter visibility.
     *
     * @return The getter visibility.
     */
    Visibility getterVisibility() default Visibility.DEFAULT;

    /**
     * Returns the is-getter visibility.
     *
     * @return The is-getter visibility.
     */
    Visibility isGetterVisibility() default Visibility.DEFAULT;

    /**
     * Returns the setter visibility.
     *
     * @return The setter visibility.
     */
    Visibility setterVisibility() default Visibility.DEFAULT;

    /**
     * Returns the field visibility.
     *
     * @return The field visibility.
     */
    Visibility fieldVisibility() default Visibility.DEFAULT;

    /**
     * Jackson auto-detect visibility values.
     */
    enum Visibility {
        /**
         * All members are visible.
         */
        ANY,

        /**
         * Non-private members are visible.
         */
        NON_PRIVATE,

        /**
         * Protected and public members are visible.
         */
        PROTECTED_AND_PUBLIC,

        /**
         * Only public members are visible.
         */
        PUBLIC_ONLY,

        /**
         * No members are visible.
         */
        NONE,

        /**
         * Use the default visibility for the accessor kind.
         */
        DEFAULT
    }
}
