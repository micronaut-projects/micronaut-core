/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.tracing.instrument.kotlin;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.bind.binders.CoroutineContextFactory;
import io.micronaut.tracing.instrument.util.TracingInvocationInstrumenterFactory;
import jakarta.inject.Singleton;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.ThreadContextElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The factory of {@link CoroutineTracingDispatcher}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Requires(classes = {ThreadContextElement.class, CoroutineContext.class})
@Context
@Singleton
@Internal
final class CoroutineTracingDispatcherFactory implements CoroutineContextFactory<CoroutineTracingDispatcher> {

    private final List<TracingInvocationInstrumenterFactory> instrumenters;

    CoroutineTracingDispatcherFactory(List<TracingInvocationInstrumenterFactory> instrumenters) {
        this.instrumenters = instrumenters;
    }

    @NotNull
    @Override
    public CoroutineTracingDispatcher create() {
        return new CoroutineTracingDispatcher(instrumenters.stream()
                .map(TracingInvocationInstrumenterFactory::newTracingInvocationInstrumenter)
                .collect(Collectors.toList()));
    }
}
