package io.micronaut.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.UserDetails;

public interface ContextAuthenticationMapper {

    UserDetails map(ConvertibleValues<Object> attributes, String username);
}
