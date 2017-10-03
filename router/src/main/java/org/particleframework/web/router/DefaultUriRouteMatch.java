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

import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.MethodExecutionHandle;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link RouteMatch} interface for matches to URIs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultUriRouteMatch<T> extends AbstractRouteMatch<T> implements UriRouteMatch<T> {

    private final HttpMethod httpMethod;
    private final UriMatchInfo matchInfo;
    private final Set<MediaType> acceptedMediaTypes;

    protected DefaultUriRouteMatch(HttpMethod httpMethod,
                                   UriMatchInfo matchInfo,
                                   MethodExecutionHandle executableMethod,
                                   List<Predicate<HttpRequest>> conditions,
                                   Set<MediaType> acceptedMediaTypes,
                                   ConversionService<?> conversionService
    ) {
        super(conditions, executableMethod, conversionService);
        this.matchInfo = matchInfo;
        this.httpMethod = httpMethod;
        this.acceptedMediaTypes = acceptedMediaTypes;
    }

    @Override
    public UriRouteMatch<T> decorate(Function<RouteMatch<T>, T> executor) {
        Map<String, Object> variables = getVariables();
        Collection<Argument> arguments = getRequiredArguments();
        RouteMatch thisRoute = this;
        return new DefaultUriRouteMatch<T>(httpMethod, matchInfo, executableMethod, conditions, acceptedMediaTypes, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return Collections.unmodifiableCollection(arguments);
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
        return contentType == null || acceptedMediaTypes.contains(contentType);
    }

    @Override
    public UriRouteMatch<T> fulfill(Map<String, Object> argumentValues) {
        Map<String, Object> oldVariables = getVariables();
        Map<String, Object> newVariables = new LinkedHashMap<>(oldVariables);
        newVariables.putAll(argumentValues);
        Set<String> argumentNames = argumentValues.keySet();
        List<Argument> requiredArguments = getRequiredArguments()
                .stream()
                .filter(arg -> !argumentNames.contains(arg.getName()))
                .collect(Collectors.toList());


        return new DefaultUriRouteMatch<T>(httpMethod, matchInfo, executableMethod, conditions, acceptedMediaTypes, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return Collections.unmodifiableCollection(requiredArguments);
            }

            @Override
            public Map<String, Object> getVariables() {
                return newVariables;
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
}
