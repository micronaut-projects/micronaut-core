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
package io.micronaut.web.router.version.resolution;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link RequestVersionResolver} responsible for extracting version from {@link io.micronaut.http.HttpParameters}.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(beans = {RoutesVersioningConfiguration.class, ParameterVersionResolverConfiguration.class})
public class ParameterVersionResolver implements RequestVersionResolver {

    private final List<String> parameterNames;

    /**
     * Creates a {@link RequestVersionResolver} to extract version from request parameter.
     *
     * @param configuration A configuration to pick correct request parameter names.
     */
    @Inject
    public ParameterVersionResolver(ParameterVersionResolverConfiguration configuration) {
        this.parameterNames = configuration.getNames();
    }

    @Override
    public Optional<String> resolve(HttpRequest<?> request) {
        return parameterNames.stream()
                .map(name -> request.getParameters().get(name))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
