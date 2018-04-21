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
import javax.inject.Singleton;
import java.net.URI;
import java.util.*;

/**
 * A security rule implementation backed by the {@link SecurityConfiguration#getInterceptUrlMap()}.
 *
 * @author James Kleeh
 * @since 1.0
 */
abstract class InterceptUrlMapRule extends AbstractSecurityRule {

    /**
     * The order of the rule
     */
    public static final Integer ORDER = 0;

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

    @Override
    public int getOrder() {
        return ORDER;
    }
}
