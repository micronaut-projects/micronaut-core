/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.AsyncRequestBodySource;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Binds the {@link AsyncRequestBody} of a request outside the binding of a method, for
 * {@link AsyncRequestBody#of(HttpRequest)}: like the argument of a filter method, it is not a
 * body of the route. What its reads left open is released when the request ends.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AsyncRequestBodyBinding implements AsyncRequestBodySource {
    private static final Argument<AsyncRequestBody> ASYNC_REQUEST_BODY = Argument.of(AsyncRequestBody.class);

    private final Supplier<RequestBinderRegistry> binderRegistry;

    /**
     * @param binderRegistry The binders of the arguments of the routes, looked up on first use
     */
    public AsyncRequestBodyBinding(Supplier<RequestBinderRegistry> binderRegistry) {
        this.binderRegistry = SupplierUtil.memoized(binderRegistry);
    }

    @Override
    public @Nullable AsyncRequestBody asyncRequestBody(HttpRequest<?> request) {
        AsyncRequestBody[] bound = new AsyncRequestBody[1];
        BasicHttpAttributes.detachRouteWaitsFor(request, () -> binderRegistry.get().findArgumentBinder(ASYNC_REQUEST_BODY)
            .flatMap(binder -> binder.bind(ConversionContext.of(ASYNC_REQUEST_BODY), request).getValue())
            .ifPresent(body -> bound[0] = body));
        return bound[0];
    }
}
