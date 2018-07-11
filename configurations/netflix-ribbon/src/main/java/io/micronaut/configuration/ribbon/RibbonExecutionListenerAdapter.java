/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.ribbon;

import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.loadbalancer.reactive.ExecutionInfo;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * An adapter for the {@link ExecutionListener} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RibbonExecutionListenerAdapter implements ExecutionListener<HttpRequest<?>, HttpResponse<?>> {
    @Override
    public void onExecutionStart(ExecutionContext<HttpRequest<?>> context) throws AbortExecutionException {
        // no-op
    }

    @Override
    public void onStartWithServer(ExecutionContext<HttpRequest<?>> context, ExecutionInfo info) throws AbortExecutionException {
        // no-op
    }

    @Override
    public void onExceptionWithServer(ExecutionContext<HttpRequest<?>> context, Throwable exception, ExecutionInfo info) {
        // no-op
    }

    @Override
    public void onExecutionSuccess(ExecutionContext<HttpRequest<?>> context, HttpResponse<?> response, ExecutionInfo info) {
        // no-op
    }

    @Override
    public void onExecutionFailed(ExecutionContext<HttpRequest<?>> context, Throwable finalException, ExecutionInfo info) {
        // no-op
    }
}
