package io.micronaut.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.UserDetails;

import javax.inject.Singleton;
import java.util.Collections;

@Singleton
public class DefaultContextAuthenticationMapper implements ContextAuthenticationMapper {

    @Override
    public UserDetails map(ConvertibleValues<Object> attributes, String username) {
        return new UserDetails(username, Collections.emptySet());
    }
}
