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

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;

import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.function.Function;

/**
 * Default implementation of the {@link RouteMatch} interface for matches to URIs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultUriRouteMatch<T> extends AbstractRouteMatch<T> implements UriRouteMatch<T> {

    private final HttpMethod httpMethod;
    private final UriMatchInfo matchInfo;
    private final Set<MediaType> acceptedMediaTypes;
    private final DefaultRouteBuilder.DefaultUriRoute uriRoute;

    DefaultUriRouteMatch(UriMatchInfo matchInfo,
                         DefaultRouteBuilder.DefaultUriRoute uriRoute,
                         ConversionService<?> conversionService
    ) {
        super(uriRoute, conversionService);
        this.uriRoute = uriRoute;
        this.matchInfo = matchInfo;
        this.httpMethod = uriRoute.httpMethod;
        this.acceptedMediaTypes = uriRoute.acceptedMediaTypes;
    }

    @Override
    public UriRouteMatch<T> decorate(Function<RouteMatch<T>, T> executor) {
        Map<String, Object> variables = getVariables();
        List<Argument> arguments = getRequiredArguments();
        RouteMatch thisRoute = this;
        return new DefaultUriRouteMatch<T>(matchInfo, uriRoute, conversionService) {
            @Override
            public List<Argument> getRequiredArguments() {
                return Collections.unmodifiableList(arguments);
            }

            @Override
            public T execute(Map argumentValues) {
                return (T) executor.apply(thisRoute);
            }

            @Override
            public Map<String, Object> getVariables() {
                return variables;
            }
        };
    }

    @Override
    public boolean accept(MediaType contentType) {
        return acceptedMediaTypes.isEmpty() || contentType == null || acceptedMediaTypes.contains(contentType);
    }

    @Override
    public UriRouteMatch<T> fulfill(Map<String, Object> argumentValues) {
        return (UriRouteMatch<T>) super.fulfill(argumentValues);
    }

    @Override
    protected RouteMatch<T> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments) {
        return new DefaultUriRouteMatch<T>(matchInfo, uriRoute, conversionService) {
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
    public String getUri() {
        return matchInfo.getUri();
    }

    @Override
    public Map<String, Object> getVariables() {
        return matchInfo.getVariables();
    }

    @Override
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String toString() {
        return httpMethod + " - " +  matchInfo.getUri();
    }
}
