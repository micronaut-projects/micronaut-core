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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionError;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Internal
final class MappedPendingRequestBindingResult<T, R> implements PendingRequestBindingResult<R> {
    private final PendingRequestBindingResult<T> source;
    private final Function<T, ArgumentBinder.BindingResult<R>> function;
    private ArgumentBinder. @Nullable BindingResult<R> second;

    MappedPendingRequestBindingResult(PendingRequestBindingResult<T> source, Function<T, ArgumentBinder.BindingResult<R>> function) {
        this.source = source;
        this.function = function;
    }

    private ArgumentBinder.BindingResult<R> computeSecond() {
        if (second == null) {
            if (source.isPending()) {
                return ArgumentBinder.BindingResult.empty();
            }
            if (!source.isSatisfied()) {
                return ArgumentBinder.BindingResult.unsatisfied();
            }
            Optional<T> first = source.getValue();
            second = first.isPresent() ? function.apply(first.get()) : ArgumentBinder.BindingResult.empty();
        }
        return second;
    }

    @Override
    public List<ConversionError> getConversionErrors() {
        List<ConversionError> conversionErrors = source.getConversionErrors();
        if (conversionErrors.isEmpty() && source.isSatisfied()) {
            conversionErrors = computeSecond().getConversionErrors();
        }
        return conversionErrors;
    }

    @Override
    public boolean isSatisfied() {
        return !isPending() && source.isSatisfied() && computeSecond().isSatisfied();
    }

    @Override
    public boolean isConvertedToArgumentType() {
        return !isPending() && source.isSatisfied() && computeSecond().isConvertedToArgumentType();
    }

    @Override
    public Optional<R> getValue() {
        return isPending() ? Optional.empty() : computeSecond().getValue();
    }

    @Override
    public boolean isPending() {
        return source.isPending() || computeSecond().isPending();
    }
}
