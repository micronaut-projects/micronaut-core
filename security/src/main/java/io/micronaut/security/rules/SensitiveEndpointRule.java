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

package io.micronaut.security.rules;

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.http.HttpRequest;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds any sensitive endpoints and processes requests that match their
 * id. The user must be authenticated to execute sensitive requests.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class SensitiveEndpointRule implements SecurityRule, ExecutableMethodProcessor<Endpoint> {

    /**
     * The order of the rule.
     */
    public static final Integer ORDER = ConfigurationInterceptUrlMapRule.ORDER + 100;

    private final EndpointConfiguration[] endpointConfigurations;
    private final EndpointDefaultConfiguration defaultConfiguration;
    private Map<Method, Boolean> endpointMethods = new HashMap<>();

    SensitiveEndpointRule(EndpointConfiguration[] endpointConfigurations,
                          EndpointDefaultConfiguration defaultConfiguration) {
        this.endpointConfigurations = endpointConfigurations;
        this.defaultConfiguration = defaultConfiguration;
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        if (routeMatch instanceof MethodBasedRouteMatch) {
            Method method = ((MethodBasedRouteMatch) routeMatch).getTargetMethod();
            if (endpointMethods.containsKey(method)) {
                Boolean sensitive = endpointMethods.get(method);
                if (claims == null && sensitive) {
                    return SecurityRuleResult.REJECTED;
                } else {
                    return SecurityRuleResult.ALLOWED;
                }
            }
        }
        return SecurityRuleResult.UNKNOWN;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Optional<String> optionalId = beanDefinition.getValue(Endpoint.class, String.class);
        optionalId.ifPresent((id) -> {

            EndpointConfiguration configuration = Arrays.stream(endpointConfigurations)
                    .filter((c) -> c.getId().equals(id))
                    .findFirst()
                    .orElseGet(() -> new EndpointConfiguration(id, defaultConfiguration));

            boolean sensitive = configuration.isSensitive().orElseGet(() -> {
                return beanDefinition
                        .getValue(Endpoint.class, "defaultSensitive", Boolean.class)
                        .orElse(Endpoint.SENSITIVE);
            });

            endpointMethods.put(method.getTargetMethod(), sensitive);
        });
    }
}
