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

package io.micronaut.web.router.version.strategy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static io.micronaut.web.router.version.RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration.PREFIX;

/**
 * A {@link VersionExtractingStrategy} responsible for extracting version from {@link io.micronaut.http.HttpHeaders}.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class HeaderVersionExtractingStrategy implements VersionExtractingStrategy {

    private final String header;

    /**
     * Creates a {@link VersionExtractingStrategy} to extract version from request header.
     *
     * @param configuration A configuration to pick correct request header name.
     */
    @Inject
    public HeaderVersionExtractingStrategy(RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration configuration) {
        this.header = configuration.getName();
    }

    @Override
    public Optional<String> extract(HttpRequest<?> request) {
        return Optional.ofNullable(request.getHeaders().get(header));
    }
}
