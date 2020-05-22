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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * Server request context invocation instrumenter
 *
 * @author dstepanov
 * @since 2.0
 */
class ServerRequestContextInvocationInstrumenter implements InvocationInstrumenter {

    private final HttpRequest<?> invocationRequest;
    private HttpRequest<?> currentRequest;
    private boolean isSet;

    /**
     * @param invocationRequest current request
     */
    public ServerRequestContextInvocationInstrumenter(HttpRequest<?> invocationRequest) {
        this.invocationRequest = invocationRequest;
        isSet = false;
    }

    /**
     * Before call.
     */
    @Override
    public void beforeInvocation() {
        currentRequest = ServerRequestContext.currentRequest().orElse(null);
        if (invocationRequest != currentRequest) {
            isSet = true;
            ServerRequestContext.set(invocationRequest);
        }
    }

    /**
     * After call.
     *
     * @param cleanup Whether to enforce cleanup
     */
    @Override
    public void afterInvocation(boolean cleanup) {
        if (isSet || cleanup) {
            ServerRequestContext.set(cleanup ? null : currentRequest);
            isSet = false;
        }
    }

}
