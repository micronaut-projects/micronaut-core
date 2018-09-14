/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.UriMatchInfo;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Default implementation of the {@link RouteMatch} interface for matches to URIs.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultUriRouteMatch<T, R> extends AbstractRouteMatch<T, R> implements UriRouteMatch<T, R> {

    private final HttpMethod httpMethod;
    private final UriMatchInfo matchInfo;
    private final DefaultRouteBuilder.DefaultUriRoute uriRoute;
    private final Charset defaultCharset;

    /**
     * @param matchInfo The URI match info
     * @param uriRoute The URI route
     * @param defaultCharset The default charset
     * @param conversionService The conversion service
     */
    DefaultUriRouteMatch(UriMatchInfo matchInfo,
                         DefaultRouteBuilder.DefaultUriRoute uriRoute,
                         Charset defaultCharset, ConversionService<?> conversionService
    ) {
        super(uriRoute, conversionService);
        this.uriRoute = uriRoute;
        this.matchInfo = matchInfo;
        this.httpMethod = uriRoute.httpMethod;
        this.defaultCharset = defaultCharset;
    }

    @Override
    public UriRouteMatch<T, R> decorate(Function<RouteMatch<R>, R> executor) {
        Map<String, Object> variables = getVariables();
        List<Argument> arguments = getRequiredArguments();
        RouteMatch thisRoute = this;
        return new DefaultUriRouteMatch<T, R>(matchInfo, uriRoute, defaultCharset, conversionService) {
            @Override
            public List<Argument> getRequiredArguments() {
                return Collections.unmodifiableList(arguments);
            }

            @Override
            public R execute(Map argumentValues) {
                return (R) executor.apply(thisRoute);
            }

            @Override
            public Map<String, Object> getVariables() {
                return variables;
            }
        };
    }

    @Override
    protected RouteMatch<R> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments) {
        return new DefaultUriRouteMatch<T, R>(matchInfo, uriRoute, defaultCharset, conversionService) {

            @Override
            public List<Argument> getRequiredArguments() {
                return Collections.unmodifiableList(requiredArguments);
            }

            @Override
            public Map<String, Object> getVariables() {
                return newVariables;
            }

            @Override
            public Optional<Argument<?>> getRequiredInput(String name) {
                return super.getRequiredInput(name);
            }
        };
    }

    @Override
    public UriRouteMatch<T, R> fulfill(Map<String, Object> argumentValues) {
        return (UriRouteMatch<T, R>) super.fulfill(argumentValues);
    }

    @Override
    public String getUri() {
        return matchInfo.getUri();
    }

    @Override
    public Map<String, Object> getVariables() {
        Map<String, Object> variables = matchInfo.getVariables();
        Map<String, Object> decoded = new LinkedHashMap<>(variables.size());
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (v instanceof CharSequence) {
                try {
                    v = URLDecoder.decode(v.toString(), defaultCharset.toString());
                } catch (UnsupportedEncodingException e) {
                    // ignore
                }
            }
            decoded.put(k, v);
        }
        return decoded;
    }

    @Override
    public boolean isExploded(String variable) {
        return matchInfo.isExploded(variable);
    }

    @Override
    public UriRoute getRoute() {
        return (UriRoute) abstractRoute;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String toString() {
        return httpMethod + " - " + matchInfo.getUri();
    }
}
