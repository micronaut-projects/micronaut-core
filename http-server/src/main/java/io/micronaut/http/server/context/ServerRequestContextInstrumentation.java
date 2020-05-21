/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;

import javax.inject.Singleton;

/**
 * Instruments Micronaut such that {@link io.micronaut.http.context.ServerRequestContext} state is propagated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
final class ServerRequestContextInstrumentation implements InvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory {

    @Override
    public InvocationInstrumenter newInvocationInstrumenter() {
        return ServerRequestContext.currentRequest().map(ServerRequestContextInvocationInstrumenter::new).orElse(null);
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newInvocationInstrumenter();
    }

}
