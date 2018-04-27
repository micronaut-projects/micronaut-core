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
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.web.router.RouteMatch;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.*;

/**
 * An abstract class with common functionality for Security Rule implementations which
 * opt to express their configuration as a List of {@link InterceptUrlMapPattern}.
 *
 * @author James Kleeh
 * @since 1.0
 */
abstract class InterceptUrlMapRule extends AbstractSecurityRule {

    /**
     * The order of the rule.
     */
    public static final Integer ORDER = 0;

    private final AntPathMatcher pathMatcher;

    /**
     *
     * @param tokenConfiguration The Token configuration.
     */
    InterceptUrlMapRule(TokenConfiguration tokenConfiguration) {
        super(tokenConfiguration);
        this.pathMatcher = PathMatcher.ANT;
    }

    /**
     * Provides a list of {@link InterceptUrlMapPattern} which will be used to provide {@link SecurityRule}.
     * @return List of {@link InterceptUrlMapPattern}
     */
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
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
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
