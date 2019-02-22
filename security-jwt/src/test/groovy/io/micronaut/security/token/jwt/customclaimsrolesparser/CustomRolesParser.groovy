package io.micronaut.security.token.jwt.customclaimsrolesparser

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.security.token.DefaultRolesParser
import io.micronaut.security.token.RolesParser
import javax.annotation.Nonnull
import javax.inject.Singleton

@CompileStatic
@Requires(property = "spec.name", value = "customclaimsrolesparser")
@Replaces(DefaultRolesParser.class)
@Singleton
class CustomRolesParser implements RolesParser {

    private static final String REALM_ACCESS_KEY = "realm_access"
    private static final String ROLES_KEY = "roles"

    @Override
    List<String> parseRoles(@Nonnull Map<String, Object> claims) {
        List<String> roles = []
        if (claims[REALM_ACCESS_KEY]) {
            if (claims[REALM_ACCESS_KEY] && claims[REALM_ACCESS_KEY] instanceof Map) {
                Map realAccessMap = (Map) claims[REALM_ACCESS_KEY]
                if ( realAccessMap[ROLES_KEY]) {
                    Object realAccess = realAccessMap[ROLES_KEY]
                    if (realAccess != null) {
                        if (realAccess instanceof Iterable) {
                            for (Object o : ((Iterable) realAccess)) {
                                roles << o.toString()
                            }
                        } else {
                            roles << realAccess.toString()
                        }
                    }
                }
            }
        }
        roles
    }
}
