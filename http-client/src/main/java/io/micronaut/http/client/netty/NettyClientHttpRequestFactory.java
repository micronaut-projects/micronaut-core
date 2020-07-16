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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequestFactory;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriTemplate;

import java.util.Map;

/**
 * Implementation of the {@link HttpRequestFactory} interface for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyClientHttpRequestFactory implements HttpRequestFactory {

    @Override
    public <T> MutableHttpRequest<T> get(String uri) {
        return new NettyClientHttpRequest<>(HttpMethod.GET, uri);
    }

    @Override
    public <T> MutableHttpRequest<T> post(String uri, T body) {
        HttpMethod method = HttpMethod.POST;
        return buildRequest(uri, body, method);
    }

    @Override
    public <T> MutableHttpRequest<T> put(String uri, T body) {
        return buildRequest(uri, body, HttpMethod.PUT);
    }

    @Override
    public <T> MutableHttpRequest<T> patch(String uri, T body) {
        return buildRequest(uri, body, HttpMethod.PATCH);
    }

    @Override
    public <T> MutableHttpRequest<T> head(String uri) {
        return new NettyClientHttpRequest<>(HttpMethod.HEAD, uri);
    }

    @Override
    public <T> MutableHttpRequest<T> options(String uri) {
        return new NettyClientHttpRequest<>(HttpMethod.OPTIONS, uri);
    }

    @Override
    public <T> MutableHttpRequest<T> delete(String uri, T body) {
        return buildRequest(uri, body, HttpMethod.DELETE);
    }

    @Override
    public <T> MutableHttpRequest<T> create(HttpMethod httpMethod, String uri) {
        return new NettyClientHttpRequest<>(httpMethod, uri);
    }

    @Override
    public <T> MutableHttpRequest<T> create(HttpMethod httpMethod, String uri, String httpMethodName) {
        return new NettyClientHttpRequest<>(httpMethod, uri, httpMethodName);
    }

    @SuppressWarnings("unchecked")
    private <T> MutableHttpRequest<T> buildRequest(String uri, T body, HttpMethod method) {
        if (uri.indexOf('{') > -1 && body != null) {
            if (body instanceof Map) {
                uri = UriTemplate.of(uri).expand((Map<String, Object>) body);
            } else {
                uri = UriTemplate.of(uri).expand(BeanMap.of(body));
            }
        }
        return new NettyClientHttpRequest<T>(method, uri).body(body);
    }
}
