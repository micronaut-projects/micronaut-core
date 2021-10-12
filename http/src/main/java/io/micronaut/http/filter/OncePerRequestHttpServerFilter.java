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
package io.micronaut.http.filter;

import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * A filter that is only executed once per request. A filter may be executed more
 * than once per request if the original route throws an exception.
 *
 * @deprecated All filters are executed once per request starting in Micronaut 3.0. Directly mplement
 * {@link HttpServerFilter} instead of extending this class and replace any usages of `micronaut.once`
 * attributes with a custom attribute name.
 * @author Graeme Rocher
 * @since 1.0
 */
@Deprecated
public abstract class OncePerRequestHttpServerFilter implements HttpServerFilter {

    @Override
    public final Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String attributeKey = getKey(getClass());
        MutableConvertibleValues<Object> attrs = request.getAttributes();
        attrs.put(attributeKey, true);
        return doFilterOnce(request, chain);
    }

    /**
     * Obtain the key used to store the attribute within a request.
     *
     * @deprecated Implement custom keys for request attributes based on the need of the filter.
     * @param filterClass the filter class
     * @return The key
     */
    @Deprecated
    public static String getKey(Class<? extends OncePerRequestHttpServerFilter> filterClass) {
        return "micronaut.once." + filterClass.getSimpleName();
    }

    /**
     * @param request The {@link HttpRequest} instance
     * @param chain   The {@link ServerFilterChain} instance
     * @return A {@link Publisher} for the Http response
     */
    protected abstract Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain);
}
