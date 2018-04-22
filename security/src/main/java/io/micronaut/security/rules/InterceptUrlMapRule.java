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

import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.*;

/**
 * A security rule implementation backed by the {@link SecurityConfiguration#getInterceptUrlMap()}.
 *
 * @author James Kleeh
 * @since 1.0
 */
abstract class InterceptUrlMapRule extends AbstractSecurityRule {

    private final AntPathMatcher pathMatcher;

    InterceptUrlMapRule(TokenConfiguration tokenConfiguration) {
        super(tokenConfiguration);
        this.pathMatcher = PathMatcher.ANT;
    }

    protected abstract List<InterceptUrlMapPattern> getPatternList();

    /**
     * If no configured pattern matches the request, return {@link SecurityRuleResult#UNKNOWN}.
     * Reads the rules in order. The first matched rule will be used for determining authorization.
     *
     * @param request The current request
     * @param routeMatch The matched route
     * @param claims The claims from the token. Null if not authenticated
     * @return The result
     */
    @Override
    public SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        final URI uri = request.getUri();
        final String uriString = uri.toString();
        final HttpMethod httpMethod = request.getMethod();

        Optional<InterceptUrlMapPattern> matchedPattern = getPatternList()
                .stream()
                .filter(p -> p.getHttpMethod().map(method -> method.equals(httpMethod)).orElse(true))
                .filter(p -> pathMatcher.matches(p.getPattern(), uriString))
                .findFirst();

        return matchedPattern
                .map(pattern -> compareRoles(pattern.getAccess(), getRoles(claims)))
                .orElse(SecurityRuleResult.UNKNOWN);
    }

}
