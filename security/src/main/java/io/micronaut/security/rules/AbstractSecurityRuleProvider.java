package io.micronaut.security.rules;

import io.micronaut.security.token.configuration.TokenConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractSecurityRuleProvider implements SecurityRuleProvider {

    private final TokenConfiguration tokenConfiguration;

    AbstractSecurityRuleProvider(TokenConfiguration tokenConfiguration) {
        this.tokenConfiguration = tokenConfiguration;
    }

    protected List<String> getRoles(Map<String, Object> claims) {
        Object rolesObject = claims.get(tokenConfiguration.getRolesClaimName());
        List<String> roles = new ArrayList<>();
        if (rolesObject instanceof Iterable) {
            for (Object o : ((Iterable) rolesObject)) {
                roles.add(o.toString());
            }
        } else {
            roles.add(rolesObject.toString());
        }
        return roles;
    }
}
