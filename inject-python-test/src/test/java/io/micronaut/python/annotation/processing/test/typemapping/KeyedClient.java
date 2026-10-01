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
package io.micronaut.python.annotation.processing.test.typemapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation with a nested enum member and a top-level enum member.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KeyedClient {

    String id() default "";

    Acknowledge acks() default Acknowledge.NONE;

    Shade shade() default Shade.LIGHT;

    int retries() default Retries.DEFAULT;

    /**
     * The acknowledgement mode.
     */
    enum Acknowledge {
        NONE,
        ONE,
        ALL
    }

    /**
     * Constants for the retries member: a nested class of constants, as annotation members commonly use.
     */
    final class Retries {

        public static final int DEFAULT = Integer.MIN_VALUE;

        public static final int NONE = 0;

        public static final int ALL = -1;

        private Retries() {
        }
    }
}
