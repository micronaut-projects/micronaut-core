/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.tree;

import io.micronaut.core.annotation.NonNull;

final class JsonBoolean extends JsonScalar {
    private static final JsonBoolean TRUE = new JsonBoolean(true);
    private static final JsonBoolean FALSE = new JsonBoolean(false);

    private final boolean value;

    private JsonBoolean(boolean value) {
        this.value = value;
    }

    static JsonBoolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    @NonNull
    @Override
    public String coerceStringValue() {
        return Boolean.toString(value);
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean getBooleanValue() {
        return value;
    }
}
