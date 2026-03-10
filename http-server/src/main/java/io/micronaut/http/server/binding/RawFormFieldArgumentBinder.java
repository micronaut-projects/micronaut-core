/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binder for {@link RawFormField}.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
@Internal
@Singleton
public final class RawFormFieldArgumentBinder implements TypedRequestArgumentBinder<RawFormField> {
    private static final Argument<RawFormField> ARGUMENT = Argument.of(RawFormField.class);

    private final BeanProvider<FormFactory> formFactory;

    RawFormFieldArgumentBinder(BeanProvider<FormFactory> formFactory) {
        this.formFactory = formFactory;
    }

    @Override
    public Argument<RawFormField> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<RawFormField> bind(ArgumentConversionContext<RawFormField> context, HttpRequest<?> source) {
        if (!(source instanceof FormCapableHttpRequest<?> fchr) || !fchr.hasFormBody()) {
            return BindingResult.unsatisfied();
        }

        Argument<RawFormField> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        CompletableFuture<RawFormField> future = Mono.from(formFactory.get().getOrCreateCompleter(fchr)
                .subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_START, argument)))
            .toFuture();

        BasicHttpAttributes.addRouteWaitsFor(source, CompletableFutureExecutionFlow.just(future));

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !future.isDone();
            }

            @Override
            public Optional<RawFormField> getValue() {
                return Optional.ofNullable(future.getNow(null));
            }
        };
    }
}
