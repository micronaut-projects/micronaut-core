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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.MultipartBody;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a {@link MultipartBody} argument.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
public class MultipartBodyArgumentBinder implements NonBlockingBodyArgumentBinder<MultipartBody> {
    private final BeanProvider<FormFactory> formFactory;

    /**
     * Default constructor.
     *
     * @param formFactory Form utilities
     */
    public MultipartBodyArgumentBinder(BeanProvider<FormFactory> formFactory) {
        this.formFactory = formFactory;
    }

    @Override
    public Argument<MultipartBody> argumentType() {
        return Argument.of(MultipartBody.class);
    }

    @Override
    public BindingResult<MultipartBody> bind(ArgumentConversionContext<MultipartBody> context, HttpRequest<?> source) {
        if (!(source instanceof FormCapableHttpRequest<?> fchr) || !fchr.hasFormBody()) {
            return BindingResult.empty();
        }
        Flux<? extends CompletedPart> parts = Flux.from(fchr.getRawFormFields())
            .flatMap(raw -> ReactiveExecutionFlow.toPublisher(formFactory.get().completePart(fchr, raw)))
            .doOnDiscard(CompletedPart.class, p -> p.closeAsync(formFactory.get().getDiskWriteExecutor()));
        return () -> Optional.of(parts::subscribe);
    }
}
