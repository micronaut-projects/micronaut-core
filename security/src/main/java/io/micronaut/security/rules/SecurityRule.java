package io.micronaut.security.rules;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Informs the JWT filter what to do with the given request.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface SecurityRule extends Ordered {

    /**
     * The token to represent allowing anonymous access.
     */
    String IS_ANONYMOUS = "isAnonymous()";

    /**
     * The token to represent allowing any authenticated access.
     */
    String IS_AUTHENTICATED = "isAuthenticated()";

    /**
     * Returns a security result based on any conditions.
     * @see SecurityRuleResult
     *
     * @param request The current request
     * @param routeMatch The matched route
     * @param claims The claims from the token. Null if not authenticated
     * @return The result
     */
    SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, @Nullable  Map<String, Object> claims);
}
