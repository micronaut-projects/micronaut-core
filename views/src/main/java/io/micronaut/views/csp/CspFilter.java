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
package io.micronaut.views.csp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

/**
 * <p>
 *      Provides support for <a href="https://www.w3.org/TR/CSP2/">Content Security Policy (CSP) Level 2</a>.
 * </p>
 *
 * <p>
 *      Content Security Policy, a mechanism web applications can use to mitigate a broad class of content
 *      injection vulnerabilities, such as cross-site scripting (XSS). Content Security Policy is a declarative
 *      policy that lets the authors (or server administrators) if a web application inform the client about
 *      the sources from which the application expects to load resources.
 * </p>
 *
 * <p>
 *     To mitigate XSS attacks, for example, a web application can declare that it only expects to load
 *     scripts from specific, trusted sources. This declaration allows the client to detect and block
 *     malicious scripts injected into the application by an attacker.
 * </p>
 *
 * <p>
 *     This implementation of {@link HttpServerFilter} writes one of the following HTTP headers:
 * </p>
 *
 * <ul>
 *     <li>Content-Security-Policy</li>
 *     <li>Content-Security-Policy-Report-Only</li>
 * </ul>
 *
 * @author Arul Dhesiaseelan
 * @since 1.1
 */
@Filter("/**")
public class CspFilter implements HttpServerFilter {

    public static final String CSP_HEADER = "Content-Security-Policy";
    public static final String CSP_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    protected final CspConfiguration cspConfiguration;

    /**
     * @param cspConfiguration The {@link CspConfiguration} instance
     */
    public CspFilter(CspConfiguration cspConfiguration) {
        this.cspConfiguration = cspConfiguration;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        return Flowable.fromPublisher(chain.proceed(request))
                .doOnNext(response -> {
                    if (cspConfiguration.isReportOnly()) {
                        response.getHeaders().add(CSP_REPORT_ONLY_HEADER, cspConfiguration.getPolicyDirectives());
                    } else {
                        response.getHeaders().add(CSP_HEADER, cspConfiguration.getPolicyDirectives());
                    }
                });
    }

}
