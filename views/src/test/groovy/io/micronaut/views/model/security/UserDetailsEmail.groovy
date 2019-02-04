package io.micronaut.views.model.security

import groovy.transform.CompileStatic
import io.micronaut.security.authentication.UserDetails

@CompileStatic
class UserDetailsEmail extends UserDetails  {
    String email

    UserDetailsEmail(String username, Collection<String> roles) {
        super(username, roles)
    }

    UserDetailsEmail(String username, Collection<String> roles, String email) {
        super(username, roles)
        this.email = email
    }

    @Override
    Map<String, Object> getAttributes() {
        Map<String, Object> result = super.getAttributes()
        result.put("email", email)
        result
    }
}
