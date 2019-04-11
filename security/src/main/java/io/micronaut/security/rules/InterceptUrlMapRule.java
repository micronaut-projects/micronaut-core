/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.security.token.RolesFinder;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
     * @deprecated use {@link InterceptUrlMapRule( RolesFinder )} instead.
     * @param tokenConfiguration The Token configuration.
     */
    @Deprecated
    public InterceptUrlMapRule(TokenConfiguration tokenConfiguration) {
        super(tokenConfiguration);
        this.pathMatcher = PathMatcher.ANT;
    }

    /**
     *
     * @param rolesFinder Roles Parser
     */
    @Inject
    public InterceptUrlMapRule(RolesFinder rolesFinder) {
        super(rolesFinder);
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
        final String path = request.getUri().getPath();
        final HttpMethod httpMethod = request.getMethod();

        Predicate<InterceptUrlMapPattern> exactMatch = p -> pathMatcher.matches(p.getPattern(), path) && p.getHttpMethod().isPresent() && httpMethod.equals(p.getHttpMethod().get());
        Predicate<InterceptUrlMapPattern> uriPatternMatch = p -> pathMatcher.matches(p.getPattern(), path) && p.getHttpMethod().map(method -> method.equals(httpMethod)).orElse(true);

        Optional<InterceptUrlMapPattern> matchedPattern = getPatternList()
                .stream()
                .filter(exactMatch)
                .findFirst();

        // if we don't get an exact match try to find a match by the uri pattern
        if (!matchedPattern.isPresent()) {
            matchedPattern = getPatternList()
                    .stream()
                    .filter(uriPatternMatch)
                    .findFirst();
        }

        return matchedPattern
                .map(pattern -> compareRoles(pattern.getAccess(), getRoles(claims)))
                .orElse(SecurityRuleResult.UNKNOWN);
    }
}
