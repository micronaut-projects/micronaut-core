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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of the {@link RouteMatch} interface for matches to URIs.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class DefaultUriRouteMatch<T, R> extends AbstractRouteMatch<T, R> implements UriRouteMatch<T, R> {

    private final UriMatchInfo matchInfo;
    private final UriRouteInfo<T, R> uriRouteInfo;
    private final Charset defaultCharset;

    /**
     * @param matchInfo The URI match info
     * @param routeInfo The URI route
     * @param defaultCharset The default charset
     * @param conversionService The conversion service
     */
    DefaultUriRouteMatch(UriMatchInfo matchInfo,
                         UriRouteInfo<T, R> routeInfo,
                         Charset defaultCharset, ConversionService conversionService
    ) {
        super(routeInfo, conversionService);
        this.matchInfo = matchInfo;
        this.uriRouteInfo = routeInfo;
        this.defaultCharset = defaultCharset;
    }

    @Override
    public String getUri() {
        return matchInfo.getUri();
    }

    @Override
    public Map<String, Object> getVariableValues() {
        Map<String, Object> variables = matchInfo.getVariableValues();
        if (CollectionUtils.isNotEmpty(variables)) {
            Map<String, Object> decoded = CollectionUtils.newLinkedHashMap(variables.size());
            variables.forEach((k, v) -> {
                if (v instanceof CharSequence) {
                    v = URLDecoder.decode(v.toString(), defaultCharset);
                }
                decoded.put(k, v);
            });
            return decoded;
        }
        return variables;
    }

    @Override
    public List<UriMatchVariable> getVariables() {
        return matchInfo.getVariables();
    }

    @Override
    public Map<String, UriMatchVariable> getVariableMap() {
        return matchInfo.getVariableMap();
    }

    @Override
    public UriRouteInfo<T, R> getRouteInfo() {
        return uriRouteInfo;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return uriRouteInfo.getHttpMethod();
    }

    @Override
    public String toString() {
        return uriRouteInfo.getHttpMethod() + " - " + matchInfo.getUri();
    }
}
