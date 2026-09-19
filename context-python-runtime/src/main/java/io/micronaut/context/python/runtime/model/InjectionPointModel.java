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
package io.micronaut.context.python.runtime.model;

import org.jspecify.annotations.Nullable;

/**
 * The resolution strategy of one injected argument, decided by the compiler.
 *
 * @param kind         The kind
 * @param argument     The injected argument
 * @param beanTypeName The bean type name for the collection, optional and map kinds
 * @param propertyName The property name for {@link Kind#PROPERTY}
 * @param propertyPath The property path for {@link Kind#PROPERTY}
 * @param value        The value expression for {@link Kind#VALUE}
 * @since 5.3.0
 */
public record InjectionPointModel(Kind kind, ArgumentModel argument, @Nullable String beanTypeName,
                                  @Nullable String propertyName, @Nullable String propertyPath, @Nullable String value) {

    /**
     * The supported injection point kinds.
     */
    public enum Kind {
        /** A bean, possibly qualified. */
        BEAN(1),
        /** All beans of the recorded type, as a collection or array. */
        BEANS(2),
        /** An optional bean. */
        OPTIONAL_BEAN(3),
        /** A configuration property. */
        PROPERTY(4),
        /** A placeholder value. */
        VALUE(5),
        /** The bean context itself. */
        BEAN_CONTEXT(6),
        /** The bean resolution context itself. */
        RESOLUTION_CONTEXT(7);

        private final int code;

        Kind(int code) {
            this.code = code;
        }

        /**
         * @return The code the codec writes for this kind
         */
        public int code() {
            return code;
        }

        /**
         * @param code A code
         * @return The kind with the code, or null
         */
        public static @Nullable Kind ofCode(int code) {
            for (Kind kind : values()) {
                if (kind.code == code) {
                    return kind;
                }
            }
            return null;
        }
    }
}
