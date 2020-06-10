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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * Server request context invocation instrumenter.
 *
 * @author dstepanov
 * @since 2.0
 */
class ServerRequestContextInvocationInstrumenter implements InvocationInstrumenter {

    private final HttpRequest<?> invocationRequest;

    /**
     * @param invocationRequest current request
     */
    public ServerRequestContextInvocationInstrumenter(HttpRequest<?> invocationRequest) {
        this.invocationRequest = invocationRequest;
    }

    /**
     * Server context instrumentation.
     * @return new instance
     */
    @NonNull
    @Override
    public Instrumentation newInstrumentation() {
        HttpRequest<?> currentRequest = ServerRequestContext.currentRequest().orElse(null);
        boolean isSet;
        if (invocationRequest != currentRequest) {
            isSet = true;
            ServerRequestContext.set(invocationRequest);
        } else {
            isSet = false;
        }
        return cleanup -> {
            if (cleanup) {
                ServerRequestContext.set(null);
            } else if (isSet) {
                ServerRequestContext.set(currentRequest);
            }
        };
    }
}
