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

import io.micronaut.http.HttpRequest;
import io.micronaut.security.Secured;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Security rule implementation for the {@link Secured} annotation.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class SecuredAnnotationRule extends AbstractSecurityRule {

    /**
     * The order of the rule.
     */
    public static final Integer ORDER = ConfigurationInterceptUrlMapRule.ORDER - 100;

    /**
     *
     * @param tokenConfiguration Token Configuration.
     */
    SecuredAnnotationRule(TokenConfiguration tokenConfiguration) {
        super(tokenConfiguration);
    }

    /**
     * Returns {@link SecurityRuleResult#UNKNOWN} if the {@link Secured} annotation is not
     * found on the method or class, or if the route match is not method based.
     *
     * @param request The current request
     * @param routeMatch The matched route
     * @param claims The claims from the token. Null if not authenticated
     * @return The result
     */
    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);
            if (methodRoute.hasAnnotation(Secured.class)) {
                Optional<String[]> optionalValue = methodRoute.getValue(Secured.class, String[].class);
                if (optionalValue.isPresent()) {
                    return compareRoles(Arrays.asList(optionalValue.get()), getRoles(claims));
                }
            }
        }
        return SecurityRuleResult.UNKNOWN;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
