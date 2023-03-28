/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.bind;

import io.micronaut.core.annotation.Internal;

import java.util.Optional;

/**
 * Wrapper around a {@link io.micronaut.core.bind.ArgumentBinder.BindingResult} that ensures
 * {@link #getValue()} is only called once.
 *
 * @param <T> The result type
 */
@Internal
public final class OneShotBindingResult<T> implements ArgumentBinder.BindingResult<T> {
    private final ArgumentBinder.BindingResult<T> actual;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<T> value;

    public OneShotBindingResult(ArgumentBinder.BindingResult<T> actual) {
        this.actual = actual;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public Optional<T> getValue() {
        Optional<T> value = this.value;
        if (value == null) {
            // Let's hope this is not used concurrently
            this.value = value = actual.getValue();
        }
        return value;
    }
}
