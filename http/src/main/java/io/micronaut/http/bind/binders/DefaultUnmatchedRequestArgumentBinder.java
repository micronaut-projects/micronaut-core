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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
     * The binders that run before the filters, in order. Computed by {@link #createSpecific} for
     * the binder of an argument. {@code null} for the binder of the registry, whose list of
     * unmatched binders can still grow.
     */
    private final RequestArgumentBinder<Object> @Nullable [] earlyBinders;
    /**
     * The postponed binders, in order, that run after the filters. Computed like {@link #earlyBinders}.
     */
    private final RequestArgumentBinder<Object> @Nullable [] postponedBinders;

    /**
     * @param internalPreUnmatchedArgumentBinders  The internal pre unmatched binders
     * @param unmatchedArgumentBinders             The unmatched binders
     * @param internalPostUnmatchedArgumentBinders The internal post unmatched binders
     */
    public DefaultUnmatchedRequestArgumentBinder(List<RequestArgumentBinder<Object>> internalPreUnmatchedArgumentBinders,
                                                 List<RequestArgumentBinder<Object>> unmatchedArgumentBinders,
                                                 List<RequestArgumentBinder<Object>> internalPostUnmatchedArgumentBinders) {
        this(internalPreUnmatchedArgumentBinders, unmatchedArgumentBinders, internalPostUnmatchedArgumentBinders, false);
    }

    private DefaultUnmatchedRequestArgumentBinder(List<RequestArgumentBinder<Object>> internalPreUnmatchedArgumentBinders,
                                                  List<RequestArgumentBinder<Object>> unmatchedArgumentBinders,
                                                  List<RequestArgumentBinder<Object>> internalPostUnmatchedArgumentBinders,
                                                  boolean fixed) {
        this.internalPreUnmatchedArgumentBinders = internalPreUnmatchedArgumentBinders;
        this.unmatchedArgumentBinders = unmatchedArgumentBinders;
        this.internalPostUnmatchedArgumentBinders = internalPostUnmatchedArgumentBinders;
        if (fixed) {
            this.earlyBinders = binders(false);
            this.postponedBinders = binders(true);
        } else {
            this.earlyBinders = null;
            this.postponedBinders = null;
        }
    }

    /**
     * @param postponed Whether to select the postponed binders or the others
     * @return The binders of the three lists, in order, that are postponed or not
     */
    @SuppressWarnings("unchecked")
    private RequestArgumentBinder<Object>[] binders(boolean postponed) {
        List<RequestArgumentBinder<Object>> binders = new ArrayList<>(
            internalPreUnmatchedArgumentBinders.size() + unmatchedArgumentBinders.size() + internalPostUnmatchedArgumentBinders.size()
        );
        addBinders(binders, internalPreUnmatchedArgumentBinders, postponed);
        addBinders(binders, unmatchedArgumentBinders, postponed);
        addBinders(binders, internalPostUnmatchedArgumentBinders, postponed);
        return binders.toArray(new RequestArgumentBinder[0]);
    }

    private static void addBinders(List<RequestArgumentBinder<Object>> target, List<RequestArgumentBinder<Object>> binders, boolean postponed) {
        for (RequestArgumentBinder<Object> binder : binders) {
            if (binder instanceof PostponedRequestArgumentBinder == postponed) {
                target.add(binder);
            }
        }
    }

    @Override
    public RequestArgumentBinder<T> createSpecific(Argument<T> argument) {
        Function<RequestArgumentBinder<Object>, RequestArgumentBinder<Object>> createSpecific = b -> b.createSpecific((Argument<Object>) argument);
        return new DefaultUnmatchedRequestArgumentBinder<>(
            internalPreUnmatchedArgumentBinders.stream().map(createSpecific).toList(),
            unmatchedArgumentBinders.stream().map(createSpecific).toList(),
            internalPostUnmatchedArgumentBinders.stream().map(createSpecific).toList(),
            true
        );
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        RequestArgumentBinder<Object>[] binders = earlyBinders;
        if (binders == null) {
            binders = binders(false);
        }
        List<PendingRequestBindingResult<?>> pending = null;
        List<ConversionError> errors = null;
        boolean allUnsatisfied = true;
        for (RequestArgumentBinder<Object> binder : binders) {
            BindingResult<?> result = binder.bind((ArgumentConversionContext<Object>) context, request);
            if (result.isPresentAndSatisfied()) {
                return (BindingResult<T>) result;
            } else if (result instanceof PendingRequestBindingResult<?> pendingRequestBindingResult) {
                if (pending == null) {
                    pending = new ArrayList<>(2);
                }
                pending.add(pendingRequestBindingResult);
                allUnsatisfied = false;
            } else {
                if (result != BindingResult.UNSATISFIED) {
                    if (errors == null) {
                        errors = new ArrayList<>(2);
                    }
                    errors.addAll(result.getConversionErrors());
                    allUnsatisfied = false;
                }
            }
        }
        if (allUnsatisfied) {
            return BindingResult.unsatisfied();
        }
        return new UnmatchedPendingResult<>(
            pending == null ? List.of() : pending,
            errors == null ? List.of() : Collections.unmodifiableList(errors)
        );
    }

    @Override
    public BindingResult<T> bindPostponed(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        RequestArgumentBinder<Object>[] binders = postponedBinders;
        if (binders == null) {
            binders = binders(true);
        }
        BindingResult<T> lastWithError = null;
        for (RequestArgumentBinder<Object> binder : binders) {
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

    /**
     * The result of the binders that are still pending or that failed.
     *
     * @param pending The pending results, in the order of the binders
     * @param errors  The conversion errors of the binders that failed
     * @param <T>     The type
     */
    private record UnmatchedPendingResult<T>(List<PendingRequestBindingResult<?>> pending,
                                             List<ConversionError> errors) implements PendingRequestBindingResult<T> {

        @Override
        public boolean isPending() {
            for (PendingRequestBindingResult<?> result : pending) {
                if (!result.isPending()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Optional<T> getValue() {
            for (PendingRequestBindingResult<?> result : pending) {
                if (!result.isPending()) {
                    return (Optional<T>) result.getValue();
                }
            }
            return Optional.empty();
        }

        @Override
        public List<ConversionError> getConversionErrors() {
            if (pending.isEmpty()) {
                return errors;
            }
            List<ConversionError> conversionErrors = new ArrayList<>(errors);
            for (PendingRequestBindingResult<?> result : pending) {
                conversionErrors.addAll(result.getConversionErrors());
            }
            return Collections.unmodifiableList(conversionErrors);
        }
    }
}
