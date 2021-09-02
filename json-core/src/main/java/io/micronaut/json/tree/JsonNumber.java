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

class JsonNumber extends JsonScalar {
    @NonNull
    private final Number value;

    JsonNumber(@NonNull Number value) {
        this.value = value;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    @NonNull
    public Number getNumberValue() {
        return value;
    }

    @NonNull
    @Override
    public String coerceStringValue() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber && ((JsonNumber) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
