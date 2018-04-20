package io.micronaut.security.rules;

import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.web.router.RouteMatch;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class InterceptUrlMapRuleProvider extends AbstractSecurityRuleProvider {

    public static final int ORDER = 0;
    private final List<InterceptUrlMapPattern> patternList;
    private final AntPathMatcher pathMatcher;

    InterceptUrlMapRuleProvider(SecurityConfiguration securityConfiguration,
                                TokenConfiguration tokenConfiguration) {
        super(tokenConfiguration);
        this.patternList = securityConfiguration.getInterceptUrlMap();
        this.pathMatcher = PathMatcher.ANT;
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, Map<String, Object> claims) {
        final URI uri = request.getUri();
        final String uriString = uri.toString();
        final HttpMethod httpMethod = request.getMethod();

        Optional<InterceptUrlMapPattern> matchedPattern = patternList
                .stream()
                .filter(p -> p.getHttpMethod().map(method -> method.equals(httpMethod)).orElse(true))
                .filter(p -> pathMatcher.matches(p.getPattern(), uriString))
                .findFirst();

        return matchedPattern.map(pattern -> {
            List<String> rolesClaim = getRoles(claims);
            List<String> allowedRoles = pattern.getAccess();
            allowedRoles.retainAll(rolesClaim);
            if (allowedRoles.isEmpty()) {
                return SecurityRuleResult.REJECTED;
            } else {
                return SecurityRuleResult.ALLOWED;
            }
        }).orElse(SecurityRuleResult.UNKNOWN);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
