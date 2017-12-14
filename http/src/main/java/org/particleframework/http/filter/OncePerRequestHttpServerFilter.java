/*
 * Copyright 2017 original authors
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
package org.particleframework.http.filter;

import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * A filter that is only executed once per request
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class OncePerRequestHttpServerFilter implements HttpServerFilter {
    @Override
    public final Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String attributeKey = getKey(getClass());
        MutableConvertibleValues<Object> attrs = request.getAttributes();
        if(attrs.contains(attributeKey)) {
            return chain.proceed(request);
        }
        else {
            attrs.put(attributeKey, true);
            return doFilterOnce(request, chain);
        }
    }

    protected abstract Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain);

    /**
     * Obtain the key used to store the attribute within a request
     * @param filterClass the filter class
     * @return The key
     */
    public static String getKey(Class<? extends OncePerRequestHttpServerFilter> filterClass) {
        return "particle.once." + filterClass.getSimpleName();
    }
}
