package io.micronaut.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.Set;

@Singleton
public class DefaultContextAuthenticationMapper implements ContextAuthenticationMapper {

    @Override
    public AuthenticationResponse map(ConvertibleValues<Object> attributes, String username, Set<String> groups) {
        return new UserDetails(username, groups);
    }
}
