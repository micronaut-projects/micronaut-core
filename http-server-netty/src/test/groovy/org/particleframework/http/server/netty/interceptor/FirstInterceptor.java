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
package org.particleframework.http.server.netty.interceptor;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.interceptor.HttpRequestInterceptor;
import org.spockframework.util.Assert;

import javax.inject.Singleton;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class FirstInterceptor implements HttpRequestInterceptor {

    @Override
    public int getOrder() {
        return TestSecurityInterceptor.POSITION - 100;
    }

    @Override
    public boolean matches(HttpRequest<?> request) {
        return request.getPath().toString().startsWith("/secure");
    }

    @Override
    public void intercept(HttpRequest<?> request, RequestInterceptionContext context) {
        request.getAttributes().put("first", true);
        Assert.that(!request.getAttributes().contains("authenticated"));
        context.proceed(request);
    }
}
