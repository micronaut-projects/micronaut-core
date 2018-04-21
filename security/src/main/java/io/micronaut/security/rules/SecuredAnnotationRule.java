package io.micronaut.security.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.Secured;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.*;

/**
 * Security rule implementation for the {@link Secured} annotation.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class SecuredAnnotationRule extends AbstractSecurityRule {

    /**
     * The order of the rule
     */
    public static final Integer ORDER = InterceptUrlMapRule.ORDER - 100;

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
    public SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
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
