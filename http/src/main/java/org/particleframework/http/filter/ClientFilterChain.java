/*
 * Copyright 2018 original authors
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

import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MutableHttpRequest;
import org.reactivestreams.Publisher;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ClientFilterChain extends FilterChain {

    Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request);

    @Override
    default Publisher<? extends HttpResponse<?>> proceed(HttpRequest<?> request) {
        if(!(request instanceof MutableHttpRequest)) {
            throw new IllegalArgumentException("A MutableHttpRequest is required");
        }
        return proceed((MutableHttpRequest)request);
    }
}
