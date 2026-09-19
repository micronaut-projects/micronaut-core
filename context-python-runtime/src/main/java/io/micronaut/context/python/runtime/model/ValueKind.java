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
 * The kinds of annotation member values the model can carry, as the tags of the codec.
 *
 * @since 5.3.0
 */
public enum ValueKind {
    STRING(1), BOOLEAN(2), BYTE(3), SHORT(4), INT(5), LONG(6), FLOAT(7), DOUBLE(8), CHAR(9), CLASS(10), ANNOTATION(11), ARRAY(12), OBJECT(13);

    private final int code;

    ValueKind(int code) {
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
    public static @Nullable ValueKind ofCode(int code) {
        for (ValueKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
