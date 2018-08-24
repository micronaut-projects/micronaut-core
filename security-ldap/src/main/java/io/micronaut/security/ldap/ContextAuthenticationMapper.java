package io.micronaut.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;

import java.util.Set;

public interface ContextAuthenticationMapper {

    AuthenticationResponse map(ConvertibleValues<Object> attributes, String username, Set<String> groups);
}
