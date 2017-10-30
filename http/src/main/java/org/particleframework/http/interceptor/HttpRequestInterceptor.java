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
package org.particleframework.http.interceptor;

import org.particleframework.core.order.Ordered;
import org.particleframework.http.HttpRequest;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

/**
 * <p>A interface for classes that can intercept an {@link org.particleframework.http.HttpRequest} and can either proceed with the request or return a modified result</p>
 *
 * <p>Note that implementation should call at least one of either {@link RequestContext#proceed(HttpRequest)} or {@link RequestContext#write(Object)} in order to proceed request processing.</p>
 *
 * <p>When {@link RequestContext#proceed(HttpRequest)} is called the interceptor will proceed to the next interceptor in the chain. The interceptors themselves can be ordered via the {@link Ordered} interface.</p>
 *
 * <p>When {@link RequestContext#write(Object)} is called then the next interceptor in the chain will not be invoked and instead the passed value will be encoded as the response.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpRequestInterceptor extends Ordered {


    /**
     * Return whether the given request should be be matched
     *
     * @param request The request
     * @return True if it should be matched. False otherwise.
     */
    boolean matches(HttpRequest<?> request);

    /**
     * Intercepts a {@link HttpRequest}
     *
     * @param request The {@link HttpRequest} instance
     * @param context The {@link RequestContext} instance
     */
    void intercept(HttpRequest<?> request, RequestContext context);


    /**
     * <p>A non-blocking request context. Consumers of this interface should call either {@link #proceed(HttpRequest)} or {@link #write(Object)}</p>
     *
     * <p>The context instance itself can be passed to other threads as necessary if blocking operations are required to implement the {@link HttpRequestInterceptor}</p>
     */
    interface RequestContext {
        /**
         * Proceed to the next interceptor or final request invocation
         *
         * @param request The current request
         */
        void proceed(HttpRequest<?> request);

        /**
         * Write an alternative response
         *
         * @param response The response to write
         * @return A {@link Publisher} with the response that fires once written successfully
         */
        <T> CompletableFuture<T> write(T response);
    }
}
