package io.micronaut.security.rules;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteMatch;

import java.util.Map;

/**
 * Informs the JWT filter what to do with the given request.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface SecurityRuleProvider extends Ordered {

    /**
     * Returns a security result based on the following conditions.
     * {@link SecurityRuleResult#ALLOWED} if the rule explicitly allows the request
     * {@link SecurityRuleResult#REJECTED} if the rule explicitly rejects the request
     * {@link SecurityRuleResult#UNKNOWN} if the rule can't make the determination
     *
     * @param request The current request
     * @param routeMatch The matched route
     * @param claims The claims from the token
     * @return The result
     */
    SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, Map<String, Object> claims);
}
