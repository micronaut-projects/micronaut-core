/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.views.thymeleaf;

import io.micronaut.http.HttpRequest;
import org.thymeleaf.context.AbstractContext;

import java.util.Locale;
import java.util.Map;

/**
 * Web-oriented implementation of the {@link org.thymeleaf.context.IContext} and {@link
 * org.thymeleaf.context.IWebContext} interfaces.
 *
 * @author Semyon Gashchenko
 * @since 1.1.0
 */
public class WebContext extends AbstractContext {

    private final HttpRequest<?> request;

    /**
     * @param request HTTP request.
     * @see AbstractContext#AbstractContext().
     */
    public WebContext(HttpRequest<?> request) {
        this.request = request;
    }

    /**
     * @param locale the locale.
     * @param request HTTP request.
     * @see AbstractContext#AbstractContext(Locale).
     */
    public WebContext(HttpRequest<?> request, Locale locale) {
        super(locale);
        this.request = request;
    }

    /**
     * @param request HTTP request.
     * @param locale the locale.
     * @param variables the variables.
     * @see AbstractContext#AbstractContext(Locale, Map).
     */
    public WebContext(HttpRequest<?> request, Locale locale, Map<String, Object> variables) {
        super(locale, variables);
        this.request = request;
    }

    /**
     * @return HTTP request.
     */
    public HttpRequest<?> getRequest() {
        return request;
    }
}
