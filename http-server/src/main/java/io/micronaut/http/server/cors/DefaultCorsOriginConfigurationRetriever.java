/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.cors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static io.micronaut.http.server.HttpServerConfiguration.CorsConfiguration;
import static io.micronaut.http.server.cors.CrossOriginUtil.getCorsOriginConfigurationForAnnotationMetadataProvider;
import static io.micronaut.http.server.cors.CrossOriginUtil.matchesOrigin;

/**
 * Default implementation of {@link CorsOriginConfigurationRetriever} that checks route-level
 * configuration before falling back to the server CORS configuration.
 *
 * @since 5.2.0
 */
@Singleton
@Internal
final class DefaultCorsOriginConfigurationRetriever implements CorsOriginConfigurationRetriever {
    private final CorsConfiguration corsConfiguration;

    /**
     * Creates the default CORS origin configuration retriever.
     *
     * @param corsConfiguration the server CORS configuration
     */
    DefaultCorsOriginConfigurationRetriever(CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    @Override
    public @Nullable CorsOriginConfiguration findCorsOriginConfiguration(@Nullable String origin,
                                                                         List<? extends RouteMatch<?>> routeMatches) {
        if (origin == null) {
            return null;
        }
        for (RouteMatch<?> routeMatch : routeMatches) {
            Optional<CorsOriginConfiguration> originConfigurationOptional =
                getCorsOriginConfigurationForAnnotationMetadataProvider(routeMatch);
            if (originConfigurationOptional.isPresent()) {
                CorsOriginConfiguration originConfiguration = originConfigurationOptional.get();
                if (matchesOrigin(originConfiguration, origin)) {
                    return originConfiguration;
                }
            }
        }
        if (!corsConfiguration.isEnabled()) {
            return null;
        }
        return corsConfiguration.getConfigurations().values().stream()
            .filter(config -> matchesOrigin(config, origin))
            .findFirst().orElse(null);
    }
}
