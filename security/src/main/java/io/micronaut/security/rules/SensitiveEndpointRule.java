package io.micronaut.security.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Finds any sensitive endpoints and processes requests that match their
 * id. The user must be authenticated to execute sensitive requests.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class SensitiveEndpointRule implements SecurityRule {

    /**
     * The order of the rule
     */
    public static final Integer ORDER = InterceptUrlMapRule.ORDER + 100;

    @Override
    public SecurityRuleResult check(HttpRequest request, RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        return SecurityRuleResult.UNKNOWN;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
