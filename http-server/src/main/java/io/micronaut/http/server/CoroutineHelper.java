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
package io.micronaut.http.server;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory;
import jakarta.inject.Singleton;
import reactor.util.context.ContextView;

import java.util.List;

/**
 * Coroutines helper.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
@Singleton
@Requires(classes = kotlin.coroutines.CoroutineContext.class)
public final class CoroutineHelper {

    private final List<HttpCoroutineContextFactory<?>> coroutineContextFactories;

    CoroutineHelper(List<HttpCoroutineContextFactory<?>> coroutineContextFactories) {
        this.coroutineContextFactories = coroutineContextFactories;
    }

    public void setupCoroutineContext(HttpRequest<?> httpRequest, ContextView contextView, PropagatedContext propagatedContext) {
        ContinuationArgumentBinder.setupCoroutineContext(httpRequest, contextView, propagatedContext, coroutineContextFactories);
    }
}
