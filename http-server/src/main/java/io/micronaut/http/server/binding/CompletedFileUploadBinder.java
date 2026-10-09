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
package io.micronaut.http.server.binding;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.binding.FormFieldFlows;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.http.form.FormCapableHttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds {@link CompletedFileUpload}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class CompletedFileUploadBinder implements TypedRequestArgumentBinder<CompletedFileUpload>, FormRequestArgumentBinder<CompletedFileUpload> {

    private static final Argument<CompletedFileUpload> STREAMING_FILE_UPLOAD_ARGUMENT = Argument.of(CompletedFileUpload.class);

    private final BeanProvider<FormFactory> formFactory;

    /** @param formFactory Form utilities */
    public CompletedFileUploadBinder(BeanProvider<FormFactory> formFactory) {
        this.formFactory = formFactory;
    }

    @Override
    public BindingResult<CompletedFileUpload> bindForFormRequest(ArgumentConversionContext<CompletedFileUpload> context,
                                                               FormCapableHttpRequest<?> request) {
        if (request.getContentType().isEmpty() || !request.hasFormBody()) {
            return BindingResult.unsatisfied();
        }

        Argument<CompletedFileUpload> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        FormRouteCompleter frc = formFactory.get().getOrCreateCompleter(request);
        // we implicitly just use the first field of this name.
        CompletableFuture<@Nullable CompletedFileUpload> completableFuture = FormFieldFlows.firstFlatMap(frc.subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_FULL, argument)),
            raw -> formFactory.get().completeFileUpload(request, raw));

        BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(completableFuture).onErrorResume(t -> ExecutionFlow.empty()));

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public Optional<CompletedFileUpload> getValue() {
                return Optional.ofNullable(completableFuture.getNow(null));
            }
        };
    }

    @Override
    public Argument<CompletedFileUpload> argumentType() {
        return STREAMING_FILE_UPLOAD_ARGUMENT;
    }
}
