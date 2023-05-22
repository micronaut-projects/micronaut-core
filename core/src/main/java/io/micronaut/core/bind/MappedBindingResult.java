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
import io.micronaut.core.convert.ConversionError;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Internal
final class MappedBindingResult<T, R> implements ArgumentBinder.BindingResult<R> {
    private final ArgumentBinder.BindingResult<T> source;
    private final Function<T, ArgumentBinder.BindingResult<R>> function;
    private ArgumentBinder.BindingResult<R> second;

    MappedBindingResult(ArgumentBinder.BindingResult<T> source, Function<T, ArgumentBinder.BindingResult<R>> function) {
        this.source = source;
        this.function = function;
    }

    private ArgumentBinder.BindingResult<R> computeSecond() {
        if (second == null) {
            Optional<T> first = source.getValue();
            if (first.isPresent()) {
                second = function.apply(first.get());
            } else {
                second = (ArgumentBinder.BindingResult<R>) source;
            }
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
        return source.isSatisfied() && computeSecond().isSatisfied();
    }

    @Override
    public Optional<R> getValue() {
        return computeSecond().getValue();
    }
}
