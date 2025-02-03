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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The binder will try to bind the argument value which wasn't matched by an annotation or a type.
 *
 * @param <T> A type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class DefaultUnmatchedRequestArgumentBinder<T> implements PostponedRequestArgumentBinder<T>, UnmatchedRequestArgumentBinder {

    private final List<RequestArgumentBinder<Object>> internalPreUnmatchedArgumentBinders;
    private final List<RequestArgumentBinder<Object>> unmatchedArgumentBinders;
    private final List<RequestArgumentBinder<Object>> internalPostUnmatchedArgumentBinders;

    /**
     * @param internalPreUnmatchedArgumentBinders  The internal pre unmatched binders
     * @param unmatchedArgumentBinders             The unmatched binders
     * @param internalPostUnmatchedArgumentBinders The internal post unmatched binders
     */
    public DefaultUnmatchedRequestArgumentBinder(List<RequestArgumentBinder<Object>> internalPreUnmatchedArgumentBinders,
                                                 List<RequestArgumentBinder<Object>> unmatchedArgumentBinders,
                                                 List<RequestArgumentBinder<Object>> internalPostUnmatchedArgumentBinders) {
        this.internalPreUnmatchedArgumentBinders = internalPreUnmatchedArgumentBinders;
        this.unmatchedArgumentBinders = unmatchedArgumentBinders;
        this.internalPostUnmatchedArgumentBinders = internalPostUnmatchedArgumentBinders;
    }

    private Stream<RequestArgumentBinder<Object>> stream() {
        return Stream.concat(
            internalPreUnmatchedArgumentBinders.stream(),
            Stream.concat(
                unmatchedArgumentBinders.stream(),
                internalPostUnmatchedArgumentBinders.stream()
            )
        );
    }

    @Override
    public RequestArgumentBinder<T> createSpecific(Argument<T> argument) {
        Function<RequestArgumentBinder<Object>, RequestArgumentBinder<Object>> createSpecific = b -> b.createSpecific((Argument<Object>) argument);
        return new DefaultUnmatchedRequestArgumentBinder<>(
            internalPreUnmatchedArgumentBinders.stream().map(createSpecific).toList(),
            unmatchedArgumentBinders.stream().map(createSpecific).toList(),
            internalPostUnmatchedArgumentBinders.stream().map(createSpecific).toList()
        );
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        List<PendingRequestBindingResult<?>> pending = new ArrayList<>();
        List<ConversionError> errors = new ArrayList<>();
        boolean allUnsatisfied = true;
        for (RequestArgumentBinder<Object> binder : stream().filter(binder -> !(binder instanceof PostponedRequestArgumentBinder)).toList()) {
            BindingResult<?> result = binder.bind((ArgumentConversionContext<Object>) context, request);
            if (result.isPresentAndSatisfied()) {
                return (BindingResult<T>) result;
            } else if (result instanceof PendingRequestBindingResult<?> pendingRequestBindingResult) {
                pending.add(pendingRequestBindingResult);
                allUnsatisfied = false;
            } else {
                if (result != BindingResult.UNSATISFIED) {
                    errors.addAll(result.getConversionErrors());
                    allUnsatisfied = false;
                }
            }
        }
        if (allUnsatisfied) {
            return BindingResult.unsatisfied();
        }
        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return pending.stream().allMatch(PendingRequestBindingResult::isPending);
            }

            @Override
            public Optional<T> getValue() {
                return pending.stream().filter(r -> !r.isPending()).findFirst().flatMap(r -> (Optional<? extends T>) r.getValue());
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return Stream.concat(errors.stream(), pending.stream().flatMap(r -> r.getConversionErrors().stream())).toList();
            }
        };
    }

    @Override
    public BindingResult<T> bindPostponed(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        BindingResult<T> lastWithError = null;
        for (RequestArgumentBinder<Object> binder : stream().filter(binder -> (binder instanceof PostponedRequestArgumentBinder)).toList()) {
            BindingResult<?> result = binder.bind((ArgumentConversionContext<Object>) context, request);
            if (result.getValue().isPresent()) {
                return (BindingResult<T>) result;
            }
            if (!result.getConversionErrors().isEmpty()) {
                lastWithError = (BindingResult<T>) result;
            }
        }
        return lastWithError == null ? BindingResult.unsatisfied() : lastWithError;
    }
}
