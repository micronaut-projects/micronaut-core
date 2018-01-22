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
package org.particleframework.web.router.resource;

import org.particleframework.core.type.Argument;
import org.particleframework.core.type.ReturnType;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.web.router.RouteMatch;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A route match designed to serve a {@link File}
 *
 * @author James Kleeh
 * @since 1.0
 */
public class StaticFileRouteMatch implements RouteMatch<File> {

    private final File file;

    public StaticFileRouteMatch(File file) {
        this.file = file;
    }

    public Map<String, Object> getVariables() {
        return Collections.emptyMap();
    }

    @Override
    public File execute(Map<String, Object> argumentValues) {
        return file;
    }

    public RouteMatch<File> fulfill(Map<String, Object> argumentValues) {
        return this;
    }

    @Override
    public RouteMatch<File> decorate(Function<RouteMatch<File>, File> executor) {
        return new StaticFileRouteMatch(executor.apply(this));
    }

    public Optional<Argument<?>> getRequiredInput(String name) {
        return Optional.empty();
    }

    public Optional<Argument<?>> getBodyArgument() {
        return Optional.empty();
    }

    @Override
    public List<MediaType> getProduces() {
        return Collections.emptyList();
    }

    public ReturnType<File> getReturnType() {
        return ReturnType.of(File.class);
    }

    @Override
    public boolean accept(@Nullable MediaType contentType) {
        return true;
    }

    @Override
    public boolean test(HttpRequest httpRequest) {
        return true;
    }
}
