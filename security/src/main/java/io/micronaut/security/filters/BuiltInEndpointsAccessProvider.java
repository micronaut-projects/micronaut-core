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

package io.micronaut.security.filters;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpMethod;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.security.config.InterceptUrlMapPattern;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provide access restrictions for the built-in endpoints /health, /beans.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class BuiltInEndpointsAccessProvider implements EndpointAccessProvider {

    protected final BeanContext beanContext;

    /**
     *
     * @param beanContext the BeanContext
     */
    public BuiltInEndpointsAccessProvider(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Optional<List<InterceptUrlMapPattern>> findEndpointAccessRestrictions() {
        return Optional.of(interceptUrlMapOfEndpointConfigurations(beanContext.getBeansOfType(EndpointConfiguration.class)));
    }

    /**
     *
     * @param endpointConfigurations Collection of {@link EndpointConfiguration}
     * @return a List of {@link InterceptUrlMapPattern}
     */
    public static List<InterceptUrlMapPattern> interceptUrlMapOfEndpointConfigurations(Collection<EndpointConfiguration> endpointConfigurations) {
        if (endpointConfigurations == null || endpointConfigurations.isEmpty()) {
            return new ArrayList<>();
        }
        List<InterceptUrlMapPattern> patterns = new ArrayList<>();
        List<String> anonymousAccess = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        List<String> authenticatedAccess = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED);
        for (HttpMethod method : Arrays.asList(HttpMethod.GET, HttpMethod.POST)) {
            patterns.addAll(endpointConfigurations.stream()
                    .filter(ec -> ec.isEnabled().isPresent() ? ec.isEnabled().get() : false)
                    .map(ec -> new InterceptUrlMapPattern(endpointPattern(ec), (ec.isSensitive().isPresent() ? ec.isSensitive().get() : false) ? authenticatedAccess : anonymousAccess, method))
                    .collect(Collectors.toList()));
        }
        return patterns;
    }

    /**
     *
     * @param ec Instance of {@link EndpointConfiguration}
     * @return / + endpoint.id
     */
    public static String endpointPattern(EndpointConfiguration ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(ec.getId());
        return sb.toString();
    }
}
