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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.NonNull;

import java.util.Objects;

/**
 * Wrapper class for any object that uses {@link System#identityHashCode} for hashCode and {@code ==} for equals. Can
 * be used to mimic {@link java.util.IdentityHashMap} behavior for other map types.
 */
final class IdentityWrapper {
    private final Object object;

    IdentityWrapper(@NonNull Object object) {
        this.object = Objects.requireNonNull(object);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IdentityWrapper iw && iw.object == this.object;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }
}
