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
package org.particleframework.web.router;

import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.PathMatcher;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.filter.HttpFilter;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;

/**
 * Default implementation of {@link FilterRoute}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultFilterRoute implements FilterRoute {

    final List<String> patterns = new ArrayList<>(1);
    final Supplier<HttpFilter> filterSupplier;
    Set<HttpMethod> httpMethods;
    private HttpFilter filter;


    DefaultFilterRoute(String pattern, Supplier<HttpFilter> filter) {
        Objects.requireNonNull(pattern, "Pattern argument is required");
        Objects.requireNonNull(pattern, "HttpFilter argument is required");
        this.filterSupplier = filter;
        this.patterns.add(pattern);
    }

    @Override
    public HttpFilter getFilter() {
        HttpFilter filter = this.filter;
        if (filter == null) {
            synchronized (this) { // double check
                filter = this.filter;
                if (filter == null) {
                    this.filter = filter =filterSupplier.get();
                }
            }
        }
        return filter;
    }

    @Override
    public Optional<HttpFilter> match(HttpMethod method, URI uri) {
        String uriStr = uri.toString();
        for (String pattern : patterns) {
            if( PathMatcher.ANT.matches(pattern, uriStr) ) {
                return Optional.of(getFilter());
            }
        }
        return Optional.empty();
    }

    @Override
    public FilterRoute pattern(String pattern) {
        if(StringUtils.isNotEmpty(pattern)) {
            this.patterns.add(pattern);
        }
        return this;
    }

    @Override
    public FilterRoute methods(HttpMethod... methods) {
        if(ArrayUtils.isNotEmpty(methods)) {
            if(httpMethods == null) httpMethods = new HashSet<>();
            httpMethods.addAll(Arrays.asList(methods));
        }
        return this;
    }
}
