/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * A route match designed to return an existing object.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class BasicObjectRouteMatch implements RouteMatch<Object> {

    private final Object object;

    /**
     * @param object An object
     */
    public BasicObjectRouteMatch(Object object) {
        this.object = object;
    }

    @Override
    public Class<?> getDeclaringType() {
        return object.getClass();
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return Collections.emptyMap();
    }

    @Override
    public Object execute(Map<String, Object> argumentValues) {
        return object;
    }

    @Override
    public RouteMatch<Object> fulfill(Map<String, Object> argumentValues) {
        return this;
    }

    @Override
    public RouteMatch<Object> decorate(Function<RouteMatch<Object>, Object> executor) {
        return new BasicObjectRouteMatch(executor.apply(this));
    }

    @Override
    public Optional<Argument<?>> getRequiredInput(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Argument<?>> getBodyArgument() {
        return Optional.empty();
    }

    @Override
    public List<MediaType> getProduces() {
        return Collections.emptyList();
    }

    @Override
    public ReturnType<?> getReturnType() {
        return ReturnType.of(object.getClass());
    }

    @Override
    public boolean doesConsume(@Nullable MediaType contentType) {
        return true;
    }

    @Override
    public boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes) {
        return true;
    }

    @Override
    public boolean doesProduce(@Nullable MediaType acceptableType) {
        return true;
    }

    @Override
    public boolean test(HttpRequest httpRequest) {
        return true;
    }
}
