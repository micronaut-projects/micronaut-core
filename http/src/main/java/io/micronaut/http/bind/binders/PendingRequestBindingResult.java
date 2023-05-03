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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.ArgumentBinder;

import java.util.function.Function;

/**
 * A variation of {@link io.micronaut.core.bind.ArgumentBinder.BindingResult} that indicates
 * that the binding result is pending and the value should be checked later.
 *
 * @param <T> The result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public interface PendingRequestBindingResult<T> extends ArgumentBinder.BindingResult<T> {

    /**
     * @return True if the result is pending - not ready to be resolved
     */
    boolean isPending();

    /**
     * @return Was the binding requirement satisfied
     */
    default boolean isSatisfied() {
        return !isPending() && ArgumentBinder.BindingResult.super.isSatisfied();
    }

    /**
     * @return Is the value present and satisfied
     */
    default boolean isPresentAndSatisfied() {
        return !isPending() && ArgumentBinder.BindingResult.super.isPresentAndSatisfied();
    }

    @Override
    @NonNull
    default <R> ArgumentBinder.BindingResult<R> flatMap(@NonNull Function<T, ArgumentBinder.BindingResult<R>> transform) {
        return new MappedPendingRequestBindingResult<>(this, transform);
    }
}
